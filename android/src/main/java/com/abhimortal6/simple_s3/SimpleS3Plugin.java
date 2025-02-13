package com.abhimortal6.simple_s3;

import static com.amazonaws.event.ProgressEvent.COMPLETED_EVENT_CODE;
import static com.amazonaws.event.ProgressEvent.FAILED_EVENT_CODE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.CognitoCredentialsProvider;
import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.mobile.client.AWSMobileClient;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferService;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;

import java.io.File;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

/**
 * AwsS3Plugin
 */
public class SimpleS3Plugin implements FlutterPlugin, MethodCallHandler, EventChannel.StreamHandler {

    private static final String TAG = "S3Native";
    private static final String CHANNEL = "simple_s3";
    private static final String EVENTS = "simple_s3_events";
    private Result parentResult;
    private final ClientConfiguration clientConfiguration;
    private TransferUtility transferUtility1;
    private Context mContext;
    private EventChannel eventChannel;
    private MethodChannel methodChannel;
    private EventChannel.EventSink events;
    private Intent tsIntent;

    public SimpleS3Plugin() {

        clientConfiguration = new ClientConfiguration();
    }

    public static void registerWith(PluginRegistry.Registrar registrar) {
        SimpleS3Plugin simpleS3Plugins = new SimpleS3Plugin();
        simpleS3Plugins.whenAttachedToEngine(registrar.context(), registrar.messenger());
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        whenAttachedToEngine(flutterPluginBinding.getApplicationContext(), flutterPluginBinding.getBinaryMessenger());
    }

    private void whenAttachedToEngine(Context applicationContext, BinaryMessenger messenger) {
        this.mContext = applicationContext;
        methodChannel = new MethodChannel(messenger, CHANNEL);
        eventChannel = new EventChannel(messenger, EVENTS);
        eventChannel.setStreamHandler(this);
        methodChannel.setMethodCallHandler(this);
        tsIntent = new Intent(mContext, TransferService.class);
        Log.d(TAG, "whenAttachedToEngine");
    }


    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        mContext = null;
        methodChannel.setMethodCallHandler(null);
        methodChannel = null;
        eventChannel.setStreamHandler(null);
        eventChannel = null;

        Log.d(TAG, "onDetachedFromEngine");
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("upload")) {
            upload(call, result);


        } else if (call.method.equals("delete")) {
            delete(call, result);
        } else {
            result.notImplemented();
        }
    }

    private void upload(@NonNull MethodCall call, @NonNull Result result) {
        startTransferService();
        parentResult = result;

        String bucketName = call.argument("bucketName");
        String filePath = call.argument("filePath");
        String s3FolderPath = call.argument("s3FolderPath");
        String fileName = call.argument("fileName");
        String poolID = call.argument("poolID");
        String region = call.argument("region");
        String subRegion = call.argument("subRegion");
        String contentType = call.argument("contentType");
        int accessControl = call.argument("accessControl");

        System.out.println(call.arguments);

        try {

            Regions parsedRegion = Regions.fromName(region);
            Regions parsedSubRegion = subRegion.length() != 0 ? Regions.fromName(subRegion) : parsedRegion;

            TransferNetworkLossHandler.getInstance(mContext.getApplicationContext());
            CognitoCredentialsProvider credentialsProvider = new CognitoCredentialsProvider(poolID, parsedRegion, clientConfiguration);
            final AmazonS3 amazonS3Client = new AmazonS3Client(credentialsProvider, com.amazonaws.regions.Region.getRegion(parsedSubRegion));
            transferUtility1 = TransferUtility
                    .builder()
                    .context(mContext)
                    .awsConfiguration(AWSMobileClient.getInstance().getConfiguration())
                    .s3Client(amazonS3Client)
                    .build();
        } catch (Exception e) {
            sendResult(false, null);
            Log.e(TAG, "onMethodCall: exception: " + e.getMessage());
        }

        String awsPath = fileName;
        if (s3FolderPath != null && !s3FolderPath.equals("")) {
            awsPath = s3FolderPath + "/" + fileName;
        }
        ObjectMetadata objectMetadata = new ObjectMetadata();
        System.out.println(fileName.substring(fileName.lastIndexOf(".") + 1));
        objectMetadata.setContentType(contentType);

        CannedAccessControlList acl;
        switch (accessControl) {
            case 1:
                acl = CannedAccessControlList.Private;
                break;
            case 3:
                acl = CannedAccessControlList.PublicReadWrite;
                break;
            case 4:
                acl = CannedAccessControlList.AuthenticatedRead;
                break;
            case 5:
                acl = CannedAccessControlList.AwsExecRead;
                break;
            case 6:
                acl = CannedAccessControlList.BucketOwnerRead;
                break;
            case 7:
                acl = CannedAccessControlList.BucketOwnerFullControl;
                break;
            case 2:
            default:
                acl = CannedAccessControlList.PublicRead;
        }

        TransferObserver transferObserver1 = transferUtility1
                .upload(bucketName, awsPath, new File(filePath), objectMetadata, acl);

        transferObserver1.setTransferListener(new Transfer());
    }


    private void delete(@NonNull MethodCall call, @NonNull Result result) {
        startTransferService();
        parentResult = result;

        String bucketName = call.argument("bucketName");
        String filePath = call.argument("filePath");
        String poolID = call.argument("poolID");
        String region = call.argument("region");
        String subRegion = call.argument("subRegion");

        try {

            Regions parsedRegion = Regions.fromName(region);
            Regions parsedSubRegion = subRegion.length() != 0 ? Regions.fromName(subRegion) : parsedRegion;

            TransferNetworkLossHandler.getInstance(mContext.getApplicationContext());

            CognitoCredentialsProvider credentialsProvider = new CognitoCredentialsProvider(poolID, parsedRegion, clientConfiguration);
            final AmazonS3 amazonS3Client = new AmazonS3Client(credentialsProvider, com.amazonaws.regions.Region.getRegion(parsedSubRegion));

            final DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(bucketName, filePath).withGeneralProgressListener(new Progress());

            Thread thread = new Thread() {
                @Override
                public void run() {
                    amazonS3Client.deleteObject(deleteObjectRequest);
                }
            };

            thread.start();
            sendResult(true, null);

        } catch (Exception e) {
            sendResult(false, null);
            Log.e(TAG, "onMethodCall: exception: " + e.getMessage());
        }
    }

    @Override
    public void onListen(Object arguments, EventChannel.EventSink events) {
        this.events = events;
    }

    @Override
    public void onCancel(Object arguments) {
        invalidateEventSink();
    }

    private void invalidateEventSink() {
        if (events != null) {
            events.endOfStream();
            events = null;
        }
    }

    private void startTransferService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final String channelId = "19910";
            String name = "UPLOAD_RECIPE";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, tsIntent, PendingIntent.FLAG_IMMUTABLE);

            // Notification manager to listen to a channel
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            NotificationManager notificationManager = mContext.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(mContext, channelId)
                    .setContentTitle("Uploading files")
                    .setContentText("Please wait...! Uploading recipe files to sortizy.")
                    .setContentIntent(pendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setSmallIcon(R.drawable.ic_notification_logo)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setOngoing(false)
                    .setSilent(true)
                    .build();

            tsIntent.putExtra(TransferService.INTENT_KEY_NOTIFICATION, notification);
            tsIntent.putExtra(TransferService.INTENT_KEY_NOTIFICATION_ID, 15);
            tsIntent.putExtra(TransferService.INTENT_KEY_REMOVE_NOTIFICATION, true);

            // Foreground service required starting from Android Oreo
            mContext.startForegroundService(tsIntent);
        } else {
            mContext.startService(tsIntent);
        }
    }

    private void sendResult(Boolean isSuccess, Integer id) {
        try {
            if (id != null) transferUtility1.cancel(id);
            mContext.stopService(tsIntent);
            parentResult.success(isSuccess);
        } catch (Exception e) {
            Log.d(TAG, e.getMessage());
        }
    }

    class Progress implements ProgressListener {
        @Override
        public void progressChanged(ProgressEvent progressEvent) {
            switch (progressEvent.getEventCode()) {

                case COMPLETED_EVENT_CODE:
                    sendResult(true, null);
                    break;
                case FAILED_EVENT_CODE:
                default:
                    sendResult(false, null);

            }
        }
    }

    class Transfer implements TransferListener {

        private static final String TAG = "Transfer";

        @Override
        public void onStateChanged(int id, TransferState state) {
            switch (state) {

                case COMPLETED:
                    Log.d(TAG, "onStateChanged: \"COMPLETED, ");
                    sendResult(true, id);
                    break;
                case WAITING:
                    Log.d(TAG, "onStateChanged: \"WAITING, ");
                    break;
                case FAILED:
                    invalidateEventSink();
                    Log.d(TAG, "onStateChanged: \"FAILED, ");
                    sendResult(false, id);
                    break;
                default:
                    Log.d(TAG, "onStateChanged: \"SOMETHING ELSE, ");
                    break;
            }
        }

        @Override
        public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {

            float percentDoNef = ((float) bytesCurrent / (float) bytesTotal) * 100;
            int percentDone = (int) percentDoNef;
            Log.d(TAG, "ID:" + id + " bytesCurrent: " + bytesCurrent + " bytesTotal: " + bytesTotal + " " + percentDone + "%");

            if (events != null) {
                events.success(percentDone);
            }
        }

        @Override
        public void onError(int id, Exception ex) {
            Log.e(TAG, "onError: " + ex);
            invalidateEventSink();
        }
    }
}
