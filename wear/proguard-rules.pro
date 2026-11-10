# WorkManager 2.10's Room implementation is instantiated by its generated name.
# Keep its no-argument constructor when using R8 full optimization.
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}

# Pending work survives app upgrades and constructs this worker through reflection.
-keep class com.example.mypersonaltimetracker.wear.WatchSyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
