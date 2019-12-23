/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.photos.library.sample.demos;

import com.google.api.core.ApiFuture;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.photos.library.sample.components.AppPanel;
import com.google.photos.library.sample.components.CreateAlbumToolPanel;
import com.google.photos.library.sample.components.UploadToAlbumToolPanel;
import com.google.photos.library.sample.factories.PhotosLibraryClientFactory;
import com.google.photos.library.sample.helpers.LogUtil;
import com.google.photos.library.sample.helpers.UIHelper;
import com.google.photos.library.sample.suppliers.ListAlbumsSupplier;
import com.google.photos.library.sample.suppliers.SearchMediaItemSupplier;
import com.google.photos.library.sample.views.*;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.proto.BatchCreateMediaItemsRequest;
import com.google.photos.library.v1.proto.ListAlbumsRequest;
import com.google.photos.library.v1.proto.SearchMediaItemsRequest;
import com.google.photos.library.v1.upload.UploadMediaItemRequest;
import com.google.photos.library.v1.upload.UploadMediaItemResponse;
import com.google.photos.types.proto.Album;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.google.photos.library.sample.Resources.TITLE;
import static com.google.photos.library.sample.demos.FilterDemo.*;
import static com.google.photos.library.sample.helpers.UIHelper.getFormattedText;

/**
 * Google Photos Library API Sample.
 *
 * <p>Sample app for album use cases: read, add and create.
 *
 * <p>This sample uses the following api methods:
 *
 * <ul>
 *   <li>batchCreateMediaItems: create media items in an album
 *   <li>createAlbum: create a new album
 *   <li>listAlbums: list albums in a library
 *   <li>searchMediaItems: list photos in an album
 *   <li>uploadMediaItem: upload a photo
 * </ul>
 */
public final class AlbumUploader {
  public static final String ALBUM_TITLE = "Photos Album Uploader";
  public static final String ALBUM_SAMPLE_IMAGE_RESOURCE = "/assets/album.png";
  public static final String ALBUM_INTRODUCTION =
      "<html>"
          + getFormattedText(
              "Google Photos Folder Album Uploader", 14 /* fontSize */, 2 /* lineMargin */)
          + getFormattedText("This app will:", 12 /* fontSize */, 2 /* lineMargin */)
          + getFormattedText(
              " - Create albums based on local folder structure", 12 /* fontSize */, 2 /* lineMargin */)
          + getFormattedText(
              " - Upload images to those albums from local folders",
              12 /* fontSize */,
              2 /* lineMargin */)
          + "</html>";

  private static final List<String> REQUIRED_SCOPES =
      ImmutableList.of(
          "https://www.googleapis.com/auth/photoslibrary.readonly",
          "https://www.googleapis.com/auth/photoslibrary.appendonly");

  private static final String FILE_ACCESS_MODE = "r";

  private AlbumUploader() {}

  /**
   * Runs the album sample. An optional path to a credentials file can be specified as the first
   * commandline argument.
   */
  public static void main(String[] args) {
    // If the first argument is set, it contains the path to the credentials file.
    Optional<String> credentialsFile = Optional.empty();

    if (args.length > 0) {
      credentialsFile = Optional.of(args[0]);
    }

    // FIXME remove this
    credentialsFile = Optional.of("client_id.json");

    UIHelper.setUp();

    ConnectToPhotosView connectToPhotosView =
        new ConnectToPhotosView(
            TITLE,
            ALBUM_INTRODUCTION,
            ALBUM_SAMPLE_IMAGE_RESOURCE,
            credentialsFile,
            getOnCredentialsSelectedFn(),
            getOnApplicationClosedFn() /* onViewClosed */);
    connectToPhotosView.showView();
  }

  private static BiConsumer<ConnectToPhotosView, String> getOnCredentialsSelectedFn() {
    return (connectToPhotosView, credentialsPath) -> {
      connectToPhotosView.hideView();
      try {
        PhotosLibraryClient client =
            PhotosLibraryClientFactory.createClient(credentialsPath, REQUIRED_SCOPES);
        connectToPhotosView.hideView();
        showAlbums(client);
      } catch (Exception e) {
        JOptionPane.showMessageDialog(connectToPhotosView, e.getMessage());
      }
    };
  }

  private static void showAlbums(PhotosLibraryClient client)
      throws IOException, FontFormatException {
    ListAlbumsRequest request = ListAlbumsRequest.getDefaultInstance();
    final ListAlbumsSupplier listAlbumsSupplier = new ListAlbumsSupplier(client, request);
    AppPanel appPanel =
        new AppPanel(ALBUM_TITLE, getOnApplicationClosedFn() /* onSignoutClicked */);
    CreateAlbumToolPanel createAlbumToolPanel =
        new CreateAlbumToolPanel(getOnCreateClickedFn(client));
    AlbumListView albumListView =
        new AlbumListView(
            appPanel,
            createAlbumToolPanel,
            listAlbumsSupplier,
            getOnAlbumClickedFn(client),
            getOnApplicationClosedFn() /* onViewClosed */);
    albumListView.showView();
  }

  private static BiConsumer<AbstractCustomView, String> getOnCreateClickedFn(
      PhotosLibraryClient client) {
    return (abstractCustomView, newAlbumTitle) -> {
      try {
        // FIXME Add dialog to select a folder for uploading
        new AlbumUploaderTask(client).uploadAlbums(new File("/Volumes/Untitled/Mis Imágenes"));
      } catch (Exception e) {
        LogUtil.logError(e);
        JOptionPane.showMessageDialog(abstractCustomView, e.getMessage());
        abstractCustomView.showView();
      }
    };
  }

  private static BiConsumer<AlbumListView, Album> getOnAlbumClickedFn(PhotosLibraryClient client) {
    return (albumListView, album) -> {
      albumListView.hideView();
      try {
        showPhotosInAlbum(albumListView, client, album, photoListView -> albumListView.showView());
      } catch (Exception e) {
        JOptionPane.showMessageDialog(albumListView, e.getMessage());
        albumListView.showView();
      }
    };
  }

  private static void showPhotosInAlbum(
      AlbumListView albumListView,
      PhotosLibraryClient client,
      Album album,
      Consumer<PhotoListView> onAlbumClosed)
      throws IOException {
    SearchMediaItemsRequest request =
        SearchMediaItemsRequest.newBuilder().setAlbumId(album.getId()).build();
    SearchMediaItemSupplier mediaItemSupplier = new SearchMediaItemSupplier(client, request);

    AppPanel appPanel =
        new AppPanel(
            ALBUM_TITLE,
            getOnBackClickedFn(albumListView),
            getOnApplicationClosedFn() /* onSignoutClicked */);
    UploadToAlbumToolPanel uploadToAlbumToolPanel =
        new UploadToAlbumToolPanel(album, getOnFileSelectedFn(client, album));

    PhotoListView photoListView =
        new PhotoListView(
            appPanel, uploadToAlbumToolPanel, mediaItemSupplier, getOnItemClicked(), onAlbumClosed);
    photoListView.showView();
  }

  private static BiConsumer<AbstractCustomView, List<String>> getOnFileSelectedFn(
      PhotosLibraryClient client, Album album) {
    return (abstractCustomView, mediaItemPaths) -> {
      PhotoListView photoListView = (PhotoListView) abstractCustomView;
      for (String mediaItemPath : mediaItemPaths) {
        try {
          UploadMediaItemRequest.Builder uploadRequestBuilder = UploadMediaItemRequest.newBuilder();
          uploadRequestBuilder
              .setFileName(mediaItemPath)
              .setDataFile(new RandomAccessFile(mediaItemPath, FILE_ACCESS_MODE));
          ApiFuture<UploadMediaItemResponse> uploadResponseFuture =
              client.uploadMediaItemCallable().futureCall(uploadRequestBuilder.build());

          // Show loading dialog while uploading
          LoadingView.getLoadingView().showView();

          uploadResponseFuture.addListener(
              getOnUploadFinished(client, photoListView, uploadResponseFuture, album),
              MoreExecutors.directExecutor());
        } catch (FileNotFoundException e) {
          LoadingView.getLoadingView().hideView();
          JOptionPane.showMessageDialog(photoListView, e.getMessage());
        }
      }
    };
  }

  private static Runnable getOnUploadFinished(
      PhotosLibraryClient client,
      PhotoListView photoListView,
      ApiFuture<UploadMediaItemResponse> uploadResponseFuture,
      Album album) {
    return () -> {
      try {
        UploadMediaItemResponse uploadResponse = uploadResponseFuture.get();
        // Check if the upload is successful
        if (uploadResponse.getUploadToken().isPresent()) {
          BatchCreateMediaItemsRequest.Builder createRequestBuilder =
              BatchCreateMediaItemsRequest.newBuilder();
          createRequestBuilder.setAlbumId(album.getId());
          createRequestBuilder
              .addNewMediaItemsBuilder()
              .getSimpleMediaItemBuilder()
              .setUploadToken(uploadResponse.getUploadToken().get());
          client.batchCreateMediaItems(createRequestBuilder.build());

          // Hide loading dialog after finishing creating
          LoadingView.getLoadingView().hideView();

          photoListView.updateView();
        }
      } catch (Exception e) {
        LoadingView.getLoadingView().hideView();
        JOptionPane.showMessageDialog(photoListView, e.getMessage());
      }
    };
  }
}
