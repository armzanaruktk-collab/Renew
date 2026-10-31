package engine;

import android.util.Log;

public class Engine {
    private static final String TAG = "tun2socks.Engine";
    private static Key currentKey;

    public static void insert(Key key) {
        currentKey = key;
        Log.i(TAG, "Engine key configured for proxy: " + (key != null ? key.getProxy() : "null"));
    }

    public static void start() {
        Log.i(TAG, "Engine started with proxy: " + (currentKey != null ? currentKey.getProxy() : "null"));
    }
}
