package de.danoeh.antennapod.service.download;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import com.turn.ttorrent.client.Client;
import com.turn.ttorrent.client.SharedTorrent;
import android.util.Log;
import de.danoeh.antennapod.AppConfig;
import de.danoeh.antennapod.PodcastApp;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.asynctask.DownloadStatus;
import de.danoeh.antennapod.util.DownloadError;
import de.danoeh.antennapod.util.StorageUtils;

public class BitTorrentDownloader extends Downloader {
    private static final String TAG = "BitTorrentDownloader";

    public BitTorrentDownloader(DownloaderCallback downloaderCallback, DownloadStatus status) {
        super(downloaderCallback, status);
    }

    @Override
    protected void download() {

        Client bitTorrentClient = null;

        try {
            InetAddress ia = InetAddress.getLocalHost();
            SharedTorrent st = SharedTorrent.fromFile(new File(status.getFeedFile().getDownload_url()), new File(status
                    .getFeedFile().getFile_url()));

            bitTorrentClient = new Client(ia, st);

            if (bitTorrentClient != null) {

                if (StorageUtils.storageAvailable(PodcastApp.getInstance())) {
                    File destination = new File(status.getFeedFile().getFile_url());
                    if (!destination.exists()) {

                        bitTorrentClient.download();
                        
                        // or, e.g. if wifi and plugged in seed for an hour or so
                        // bitTorrentClient.share(3600);

                        // bitTorrentClient.waitForCompletion();
                        
                        // stop() is in finally

                        status.setStatusMsg(R.string.download_running);
                        status.setSize(DownloadStatus.SIZE_UNKNOWN);

                        long freeSpace = StorageUtils.getFreeSpaceAvailable();

                        if (AppConfig.DEBUG)
                            Log.d(TAG, "Free space is " + freeSpace);

                        if (status.getSize() == DownloadStatus.SIZE_UNKNOWN || status.getSize() <= freeSpace) {
                            if (AppConfig.DEBUG)
                                Log.d(TAG, "Starting download");
                            if (cancelled) {
                                onCancelled();
                            } else {
                                onSuccess();
                            }
                        } else {
                            onFail(DownloadError.ERROR_NOT_ENOUGH_SPACE, null);
                        }
                    } else {
                        Log.w(TAG, "File already exists");
                        onFail(DownloadError.ERROR_FILE_EXISTS, null);
                    }
                } else {
                    onFail(DownloadError.ERROR_DEVICE_NOT_FOUND, null);

                }
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_MALFORMED_URL, e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_IO_ERROR, e.getMessage());
        } catch (NullPointerException e) {
            e.printStackTrace();
            onFail(DownloadError.ERROR_CONNECTION_ERROR, status.getFeedFile().getDownload_url());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } finally {
            bitTorrentClient.stop();
        }
    }

    private void onSuccess() {
        if (AppConfig.DEBUG)
            Log.d(TAG, "Download was successful");
        status.setSuccessful(true);
        status.setDone(true);
        
    }

    private void onFail(int reason, String reasonDetailed) {
        if (AppConfig.DEBUG) {
            Log.d(TAG, "Download failed");
        }
        status.setReason(reason);
        status.setReasonDetailed(reasonDetailed);
        status.setDone(true);
        status.setSuccessful(false);
        cleanup();
    }

    private void onCancelled() {
        if (AppConfig.DEBUG)
            Log.d(TAG, "Download was cancelled");
        status.setReason(DownloadError.ERROR_DOWNLOAD_CANCELLED);
        status.setDone(true);
        status.setSuccessful(false);
        status.setCancelled(true);
        cleanup();
    }

    /** Deletes unfinished downloads. */
    private void cleanup() {
        if (status != null && status.getFeedFile() != null && status.getFeedFile().getFile_url() != null) {
            File dest = new File(status.getFeedFile().getFile_url());
            if (dest.exists()) {
                boolean rc = dest.delete();
                if (AppConfig.DEBUG)
                    Log.d(TAG, "Deleted file " + dest.getName() + "; Result: " + rc);
            } else {
                if (AppConfig.DEBUG)
                    Log.d(TAG, "cleanup() didn't delete file: does not exist.");
            }
        }
    }

}
