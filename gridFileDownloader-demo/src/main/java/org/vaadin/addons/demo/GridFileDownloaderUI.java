package org.vaadin.addons.demo;
/*
 * Copyright 2015 Vaadin Ltd.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.annotation.WebServlet;

import org.vaadin.gridfiledownloader.GridFileDownloader;

import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.data.provider.ListDataProvider;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.shared.ui.grid.HeightMode;
import com.vaadin.ui.Grid;
import com.vaadin.ui.Grid.SelectionMode;
import com.vaadin.ui.Label;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

@Push
@SuppressWarnings("serial")
@Theme("gridfiledownloader")
public class GridFileDownloaderUI extends UI {

    @WebServlet(
        value = "/*",
        asyncSupported = true)
    @VaadinServletConfiguration(
        productionMode = false,
        ui = GridFileDownloaderUI.class,
        widgetset = "org.vaadin.gridfiledownloader.GridFileDownloaderWidgetset")
    public static class Servlet extends VaadinServlet {
    }

    private DownloadPojo downloadPojo;

    @SuppressWarnings("unchecked")
    @Override
    protected void init(final VaadinRequest request) {
        final VerticalLayout layout = new VerticalLayout();
        layout.setMargin(true);
        layout.addComponent(new Label("asdsad"));
        setContent(layout);

        // final Grid<DownloadPojo> grid = new Grid<>("Attachment grid", new ListDataProvider<>(genDownloadPojos(5)));
        //
        // grid.setHeightMode(HeightMode.ROW);
        // grid.setHeightByRows(5);
        // grid.setSelectionMode(SelectionMode.NONE);
        //
        // grid.addColumn(DownloadPojo::getName)
        // .setCaption("Id")
        // .setId("filename")
        // .setExpandRatio(1)
        // .setCaption("File name");
        //
        // // VB FW-606 column.setCaption("File name");
        //
        // layout.addComponent(grid);
        // addGridFileDownloader(grid);

        // set tooltip for the default download column
        // VB FW-606
        // grid.setCellDescriptionGenerator(new CellDescriptionGenerator() {
        //
        // @Override
        // public String getDescription(final CellReference cell) {
        // if (FontAwesome.DOWNLOAD.equals(cell.getPropertyId())) {
        // return "download";
        // }
        // return null;
        // }
        // });

        // clear the header
        // VB FW-606
        // final HeaderCell downloadHeader = grid.getHeaderRow(0)
        // .getCell(FontAwesome.DOWNLOAD);
        // downloadHeader.setHtml("");

    }

    private List<DownloadPojo> genDownloadPojos(final int quantity) {
        final List<DownloadPojo> result = new ArrayList<>();

        for (int i = 1; i <= quantity; ++i) {
            final DownloadPojo cp = new DownloadPojo(i);
        }

        return result;

    }

    /**
     * Adds a GridFileDownloader extension that adds a download column to the Grid since no existing propertyId is specified.
     *
     * @param grid
     */
    private void addGridFileDownloader(final Grid grid) {
        new GridFileDownloader(grid, new GridFileDownloader.GridStreamResource() {

            @Override
            public InputStream getStream() {
                final byte[] data = GridFileDownloaderUI.this.downloadPojo.getData();
                if (data == null) {
                    return new ByteArrayInputStream(new byte[0]);
                }
                return new ByteArrayInputStream(data);
            }

            @Override
            public String getFilename() {
                return GridFileDownloaderUI.this.downloadPojo.getName();
            }

        }) {

        };
    }

    public class DownloadPojo implements Serializable {
        private static final long serialVersionUID = 1L;

        String name;
        String download;

        public DownloadPojo(final int selectedRow) {
            this.name = "file " + selectedRow + ".txt";
        }

        public byte[] getData() {
            final int writeAtOnce = 1024 * 1024 * 1024;
            final byte[] b = new byte[writeAtOnce];
            return b;
        }

        public String getName() {
            return this.name;
        }

    }

}