/*
 * Copyright 2015-2016 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.vaadin.gridfiledownloader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.vaadin.gridfiledownloader.client.GridFileDownloaderServerRpc;
import org.vaadin.gridfiledownloader.client.GridFileDownloaderState;

import com.vaadin.annotations.StyleSheet;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.server.ConnectorResource;
import com.vaadin.server.DownloadStream;
import com.vaadin.server.FileDownloader;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.Resource;
import com.vaadin.server.StreamResource;
import com.vaadin.server.StreamResource.StreamSource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinResponse;
import com.vaadin.server.VaadinSession;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.DateField;
import com.vaadin.ui.Grid;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.components.grid.MultiSelectionModel;
import com.vaadin.ui.renderers.HtmlRenderer;
import com.vaadin.ui.renderers.Renderer;

/**
 * This specialises {@link FileDownloader} for grid so that both the file name and content can be determined on-demand, i.e. when the user has clicked on the
 * download column of some row.
 */
@StyleSheet("gridfiledownloader.css")
public class GridFileDownloader<BEANTYPE> extends FileDownloader {

    private static Logger getLogger() {
        return Logger.getLogger(GridFileDownloader.class.getName());
    }

    /**
     * Provide both the {@link StreamSource} and the filename in an on-demand way.
     */
    public interface GridStreamResource extends StreamSource {
        String getFilename();
    }

    private static final long serialVersionUID = 1L;
    private final GridStreamResource gridStreamResource;
    private Grid<BEANTYPE> grid;
    private Object downloadColumnId;
    private BEANTYPE rowBean;
    private final GridFileDownloaderServerRpc rpc = rowIndex -> {
        final ListDataProvider<BEANTYPE> provider = (ListDataProvider<BEANTYPE>) GridFileDownloader.this.grid.getDataProvider();
        if (rowIndex != null) {
            final List<BEANTYPE> allItems = new ArrayList<>(provider.getItems());
            setRowBean(allItems.get(rowIndex));
        }
    };

    /**
     * FileDownloader extension that adds a download column to the Grid. Note that if the order or count of the columns or selection mode changes you need to
     * call {@link #recalculateDownloadColumn()} explicitly.
     *
     * @param grid
     * @param gridStreamResource
     */
    public GridFileDownloader(final Grid<BEANTYPE> grid, final GridStreamResource gridStreamResource) {
        this(grid, null, gridStreamResource);
    }

    /**
     * FileDownloader extension that adds a download behaviour to the given column of the Grid. Note that if the order or count of the columns or selection mode
     * change you need to call {@link #recalculateDownloadColumn()} explicitly.
     *
     * @param grid
     * @param downloadPropertyId
     * @param gridStreamResource
     */
    public GridFileDownloader(final Grid<BEANTYPE> grid, final Object downloadPropertyId, final GridStreamResource gridStreamResource) {
        super(new StreamResource(gridStreamResource, ""));
        assert gridStreamResource != null : "The given on-demand stream resource may never be null!";
        assert grid != null : "The given grid may never be null!";

        this.gridStreamResource = gridStreamResource;
        registerRpc(this.rpc);
        extend(grid);
        if (downloadPropertyId == null) {
            addDownloadColumn();
        }
        else {
            setDownloadColumn(downloadPropertyId);
        }
        grid.setStyleGenerator(bean -> {
            if (GridFileDownloader.this.downloadColumnId.equals(bean)) {
                return "gridfiledownloader-downloadcolumn";
            }
            return null;
        });
    }

    @Override
    public boolean handleConnectorRequest(final VaadinRequest request, final VaadinResponse response, final String path) throws IOException {

        if (!path.matches("dl(/.*)?")) {
            // Ignore if it isn't for us
            return false;
        }

        boolean markedProcessed = false;
        try {
            if (!waitForRPC()) {
                handleRPCTimeout();
                return false;
            }
            getResource().setFilename(this.gridStreamResource.getFilename());

            final VaadinSession session = getSession();

            session.lock();
            DownloadStream stream;

            try {
                final Resource resource = getFileDownloadResource();
                if (!(resource instanceof ConnectorResource)) {
                    return false;
                }
                stream = ((ConnectorResource) resource).getStream();

                if (stream.getParameter("Content-Disposition") == null) {
                    // Content-Disposition: attachment generally forces download
                    stream.setParameter("Content-Disposition", "attachment; filename=\"" + stream.getFileName() + "\"");
                }

                // Content-Type to block eager browser plug-ins from hijacking
                // the file
                if (isOverrideContentType()) {
                    stream.setContentType("application/octet-stream;charset=UTF-8");
                }
            }
            finally {
                try {
                    markProcessed();
                    markedProcessed = true;
                }
                finally {
                    session.unlock();
                }
            }
            try {
                stream.writeResponse(request, response);
            }
            catch (final Exception e) {
                handleWriteResponseException(e);
            }
            return true;
        }
        finally {
            // ensure the download request always gets marked processed
            if (!markedProcessed) {
                markProcessed();
            }
        }
    }

    protected void handleRPCTimeout() {
        markProcessed();
        GridFileDownloader.getLogger()
            .severe("Download attempt timeout before receiving RPC call about row");
    }

    /**
     * Wait until RPC call has reached the server-side with the rowId.
     */
    protected boolean waitForRPC() {
        int counter = 0;
        while (counter < 30) {
            if (getRowBean() != null) {
                return true;
            }
            try {
                Thread.sleep(100);
            }
            catch (final InterruptedException ignore) {
            }
            finally {
                ++counter;
            }
        }
        return getRowBean() != null;
    }

    /**
     * Override this method if you want more specific failure handling for write response exceptions. The main point of this is to keep the exceptions from e.g.
     * user cancelling the download from showing up in tooltip for the Grid.
     *
     * @param e
     */
    protected void handleWriteResponseException(final Exception e) {
        e.printStackTrace();
        for (final Type type : Type.values()) {
            if (type.getStyle()
                .equals(getState().failureNotificationType)) {
                Notification.show(getState().failureCaption, getState().failureDescription, type);
                return;
            }
        }
    }

    protected void markProcessed() {
        setRowBean(null);
        getState().processing = !getState().processing;
        markAsDirty();
    }

    private StreamResource getResource() {
        return (StreamResource) getFileDownloadResource();
    }

    protected void setRowBean(final BEANTYPE rowBean) {
        this.rowBean = rowBean;
    }

    protected BEANTYPE getRowBean() {
        return this.rowBean;
    }

    @Override
    protected GridFileDownloaderState getState() {
        return (GridFileDownloaderState) super.getState();
    }

    /**
     * DO NOT CALL THIS EXPLICITLY! The behaviour of this extension is not guaranteed if the target changes from the default.
     */
    @Override
    public void extend(final AbstractComponent target) {
        if (target instanceof Grid) {
            this.grid = (Grid<BEANTYPE>) target;
            super.extend(target);
        }
        else {
            throw new IllegalArgumentException("Target must be instance of Grid");
        }
    }

    /**
     * Sets the download column. Note that thanks to the workaround for how columns are recognised {@link #recalculateDownloadColumn()} must be explicitly
     * called every time column order or count or selection mode changes.
     *
     * @param propertyId
     */
    protected void setDownloadColumn(final Object propertyId) {
        this.downloadColumnId = propertyId;
        recalculateDownloadColumn();
    }

    /**
     * <b>This method must be called every time column order or count or selection mode changes</b>, otherwise download requests might get calculated for the
     * wrong column.
     */
    public void recalculateDownloadColumn() {
        getState().downloadColumnIndex = this.grid.getColumns()
            .indexOf(this.grid.getColumn((String) this.downloadColumnId));
        if (this.grid.getSelectionModel() instanceof MultiSelectionModel) {
            // MultiSelection adds extra column to the grid
            ++getState().downloadColumnIndex;
        }
    }

    /**
     * Adds a download column with propertyId {@link FontAwesome#DOWNLOAD} to the Grid and registers it with this extension.
     *
     * @see GridFileDownloader#setDownloadColumn(Object)
     */
    protected void addDownloadColumn() {
        //// VB TODO FW-606 final Indexed dataSource = this.grid.getContainerDataSource();
        final FontAwesome icon = FontAwesome.DOWNLOAD;
        // VB TODO FW-606 dataSource.addContainerProperty(icon, String.class, createDownloadHtml());
        // VB TODO FW-606 column id "download" below is dummy -> replace afterwards

        final Renderer<String> htmlRenderer = new HtmlRenderer();
        this.grid.addComponentColumn(bean -> {
            final DateField df = new DateField();
            return df;
        });
        // VB TODO FW-606
        // this.grid.getColumn("download")
        // .setRenderer(htmlRenderer);
        // this.grid.getHeaderRow(0)
        // .getCell(icon)
        // .setHtml(createDownloadHtml());
        // this.grid.getColumn("download")
        // .setSortable(false);
        // setDownloadColumn(icon);
    }

    /**
     * Creates the HTML content of the generated download column.
     *
     * @return HTML content as String
     */
    protected String createDownloadHtml() {
        return FontAwesome.DOWNLOAD.getHtml();
    }
}
