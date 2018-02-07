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
package org.vaadin.gridfiledownloader.client;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.vaadin.gridfiledownloader.GridFileDownloader;

import com.google.gwt.dom.client.Style.TextAlign;
import com.google.gwt.event.dom.client.ClickEvent;
import com.vaadin.client.ComponentConnector;
import com.vaadin.client.ServerConnector;
import com.vaadin.client.annotations.OnStateChange;
import com.vaadin.client.extensions.FileDownloaderConnector;
import com.vaadin.client.ui.VNotification;
import com.vaadin.client.widget.grid.events.BodyClickHandler;
import com.vaadin.client.widget.grid.events.GridClickEvent;
import com.vaadin.client.widgets.Grid;
import com.vaadin.shared.ui.Connect;

import elemental.json.JsonObject;

@Connect(GridFileDownloader.class)
public class GridFileDownloaderConnector extends FileDownloaderConnector implements BodyClickHandler {

    private final GridFileDownloaderServerRpc rpc = getRpcProxy(GridFileDownloaderServerRpc.class);
    private Grid<JsonObject> grid;
    private boolean processing = false;
    private static Logger logger = Logger.getLogger(GridFileDownloaderConnector.class.getName());

    @SuppressWarnings("unchecked")
    @Override
    protected void extend(final ServerConnector target) {
        this.grid = (Grid<JsonObject>) ((ComponentConnector) target).getWidget();
        this.grid.addBodyClickHandler(this);
    }

    @OnStateChange("processing")
    void processed() {
        // End of processing is marked by toggle, actual value in state is
        // irrelevant. Only log toggles that change field contents here (i.e.
        // not the initial setting of the state).
        if (this.processing != false) {
            this.processing = false;
            GridFileDownloaderConnector.logger.log(Level.FINE, "GridFileDownloader: processing click done");
        }
    }

    @Override
    public void onClick(final ClickEvent event) {
        // NOP
    }

    @Override
    public void onClick(final GridClickEvent event) {
        final int columnIndex = event.getTargetCell()
            .getColumnIndex();
        final GridFileDownloaderState gridFileDownloaderState = (GridFileDownloaderState) getState();
        if (gridFileDownloaderState.downloadColumnIndex != null && columnIndex == gridFileDownloaderState.downloadColumnIndex.intValue()) {
            event.stopPropagation();
            if (!this.processing) {
                this.processing = true;
                GridFileDownloaderConnector.logger.log(Level.FINE, "GridFileDownloader: started to process click");
                this.rpc.download(event.getTargetCell()
                    .getRowIndex());
                GridFileDownloaderConnector.super.onClick(null);
            }
            else {
                downloadIgnoredBecauseProcessing();
            }
        }
    }

    /**
     * Trigger the download for the given column and row from some other ClickEvent than GridClickEvent. This might be required if only parts of the download
     * column should trigger the download. As this method needs to be called specifically, there is no check that the column index corresponds with the download
     * column in this case.
     *
     * @param columnIndex
     * @param rowIndex
     */
    public void remoteClick(final int columnIndex, final int rowIndex) {
        if (!this.processing) {
            this.processing = true;
            GridFileDownloaderConnector.logger.log(Level.FINE, "GridFileDownloader: started to process click");
            this.rpc.download(rowIndex);
            GridFileDownloaderConnector.super.onClick(null);
        }
        else {
            downloadIgnoredBecauseProcessing();
        }
    }

    /**
     * Display notification for informing the user that a new download couldn't be triggered because previous download is still processing.
     */
    protected void downloadIgnoredBecauseProcessing() {
        final GridFileDownloaderState gridFileDownloaderState = (GridFileDownloaderState) getState();

        final VNotification n = VNotification.createNotification(gridFileDownloaderState.notificationDelay, this.grid);
        n.getElement()
            .getStyle()
            .setTextAlign(TextAlign.LEFT);
        n.show("<h1>" + gridFileDownloaderState.processingCaption + "</h1><br />" + gridFileDownloaderState.processingDescription.replace("\n", "<br/>\n"),
               VNotification.CENTERED, gridFileDownloaderState.processingNotificationType);
    }
}
