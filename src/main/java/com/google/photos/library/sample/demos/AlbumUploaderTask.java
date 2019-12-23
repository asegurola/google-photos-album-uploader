package com.google.photos.library.sample.demos;

import com.google.photos.library.sample.helpers.LogUtil;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.proto.BatchCreateMediaItemsRequest;
import com.google.photos.library.v1.proto.ListAlbumsRequest;
import com.google.photos.library.v1.proto.ListAlbumsResponse;
import com.google.photos.library.v1.upload.UploadMediaItemRequest;
import com.google.photos.library.v1.upload.UploadMediaItemResponse;
import com.google.photos.types.proto.Album;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class AlbumUploaderTask {

    private Map<String, Album> albumMap = new HashMap<>();

    private final PhotosLibraryClient client;
    private boolean startUploading;

    public AlbumUploaderTask(PhotosLibraryClient client) {
        this.client = client;
    }

    public final void uploadAlbums(File rootPhotoFolder) {
        try {
            fetchExistingAlbums();
            uploadVisitor(rootPhotoFolder, "");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void fetchExistingAlbums() {
        try {

            ListAlbumsRequest.Builder requestBuilder = ListAlbumsRequest.newBuilder()
                    .setExcludeNonAppCreatedData(false);

            Set<String> knownPageTokens = new HashSet<>();
            String nextPageToken = null;
            do {
                if (nextPageToken != null) {
                    knownPageTokens.add(nextPageToken);
                    requestBuilder.setPageToken(nextPageToken);
                }

                ListAlbumsResponse listAlbumsResponse = client.listAlbumsCallable().futureCall(
                        requestBuilder.build()).get();
                LogUtil.log("NextPage Token: " + listAlbumsResponse.getNextPageToken());
                processAlbumPage(listAlbumsResponse);
                nextPageToken = listAlbumsResponse.getNextPageToken();
            } while (nextPageToken != null && !knownPageTokens.contains(nextPageToken));

            LogUtil.log("Album Fetch Finished");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void processAlbumPage(ListAlbumsResponse listAlbumsResponse) {
        LogUtil.log("Albums: " + listAlbumsResponse.getAlbumsCount());
        for (Album album : listAlbumsResponse.getAlbumsList()) {
            albumMap.put(album.getTitle(), album);
            LogUtil.log("Loading existing album: " + album.getTitle());
        }
    }

    private void uploadVisitor(File folder, String albumPrefix) throws ExecutionException, InterruptedException, FileNotFoundException {
        for (File file : folder.listFiles()) {
            if (isImage(file)) {
                uploadImage(file, albumPrefix);
            } else if (file.isDirectory()) {
                uploadVisitor(file, (albumPrefix.isEmpty() ? "" : albumPrefix + "-") + file.getName());
            }
        }
    }

    private boolean isImage(File file) {
        if (file.isDirectory()) {
            return false;
        }
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith("png") || fileName.endsWith("gif") || fileName.endsWith("jpeg") || fileName.endsWith("jpg");
    }

    private void uploadImage(File image, String albumName) throws ExecutionException, InterruptedException, FileNotFoundException {
        if (image.getAbsolutePath().equals("/Volumes/Untitled/Mis Imágenes/2007/1173230490_f.jpg")) {
            this.startUploading = true;
            return;
        }

        if (!startUploading) {
            return;
        }

        Album album = albumMap.get(albumName);

        if (album == null) {
            album = createAlbum(albumName);
        }

        UploadMediaItemResponse response = client.uploadMediaItemCallable().futureCall(
                UploadMediaItemRequest.newBuilder()
                        .setFileName(image.getAbsolutePath())
                        .setDataFile(new RandomAccessFile(image.getAbsolutePath(), "r")).build()).get();
        if (response.getError().isPresent()) {
            LogUtil.logError("Error Uploading image: " + albumName + "  Image:" + image.getName());
            throw new RuntimeException(response.getError().get().getCause());
        } else {
            LogUtil.log("Uploaded image: " + image.getAbsolutePath());
        }

        BatchCreateMediaItemsRequest.Builder createRequestBuilder =
                BatchCreateMediaItemsRequest.newBuilder();
        createRequestBuilder.setAlbumId(album.getId());
        createRequestBuilder
                .addNewMediaItemsBuilder()
                .getSimpleMediaItemBuilder()
                .setUploadToken(response.getUploadToken().get());
        client.batchCreateMediaItems(createRequestBuilder.build());
        LogUtil.log("Added to Album: " + albumName + "  Image:" + image.getName());

    }

    private Album createAlbum(String albumName) {
        LogUtil.log("Creating album: " + albumName);
        Album album = client.createAlbum(Album.newBuilder().setTitle(albumName).build());
        albumMap.put(albumName, album);
        return album;
    }
}
