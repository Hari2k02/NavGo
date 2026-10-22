package app.navgo;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import app.navgo.core.PacketCodec;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressLint("MissingPermission")
final class BleServer implements AutoCloseable {
    private final Context context;
    private final Handler handler;
    private final Consumer<String> fatal;
    private final TrustStore trust;
    private BluetoothGattServer server;
    private BluetoothLeAdvertiser advertiser;
    private boolean closed;
    private List<byte[]> frames;
    private int frameIndex;
    private int sequence;
    private long batchAt;
    private String batchAddress;
    private final UUID serviceId = UUID.fromString(PacketCodec.SERVICE_UUID);
    private final UUID dataId = UUID.fromString(PacketCodec.DATA_UUID);

    BleServer(Context context, Handler handler, Consumer<String> fatal) {
        this.context = context;
        this.handler = handler;
        this.fatal = fatal;
        trust = new TrustStore(context);
    }

    void start() {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            fatal.accept("Bluetooth is off or unavailable");
            return;
        }
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (!adapter.isMultipleAdvertisementSupported() || advertiser == null) {
            fatal.accept("This phone cannot advertise a BLE service");
            return;
        }
        server = manager.openGattServer(context, callback);
        if (server == null) {
            fatal.accept("Android could not open the BLE server");
            return;
        }
        BluetoothGattService service = new BluetoothGattService(serviceId, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        service.addCharacteristic(new BluetoothGattCharacteristic(dataId,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM));
        if (!server.addService(service)) fatal.accept("Android rejected the navigation BLE service");
    }

    private final AdvertiseCallback advertising = new AdvertiseCallback() {
        @Override public void onStartSuccess(AdvertiseSettings settings) {
            handler.post(() -> {
                if (!closed) NavStore.status = "Ready. Connect from NavGo on your watch.";
            });
        }

        @Override public void onStartFailure(int code) {
            handler.post(() -> {
                if (!closed) fatal.accept("BLE advertising failed (code " + code + ")");
            });
        }
    };

    private final BluetoothGattServerCallback callback = new BluetoothGattServerCallback() {
        @Override public void onServiceAdded(int status, BluetoothGattService service) {
            handler.post(() -> guarded(() -> {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    fatal.accept("BLE service registration failed (code " + status + ")");
                    return;
                }
                AdvertiseData data = new AdvertiseData.Builder()
                    .addServiceUuid(new ParcelUuid(serviceId)).build();
                AdvertiseData response = new AdvertiseData.Builder()
                    .addServiceData(new ParcelUuid(serviceId), trust.phoneCode()).build();
                AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                    .setConnectable(true).setTimeout(0).build();
                advertiser.startAdvertising(settings, data, response, advertising);
            }));
        }

        @Override public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            handler.post(() -> guarded(() -> {
                String address = device.getAddress();
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    String approved = trust.approved();
                    if (!approved.isEmpty() && !approved.equals(address)) {
                        server.cancelConnection(device);
                        return;
                    }
                    if (approved.equals(address)) {
                        NavStore.connectedAddress = address;
                        NavStore.status = "Watch connected. Waiting for encrypted reads.";
                    } else if (NavStore.pendingAddress == null || NavStore.pendingAddress.equals(address)) {
                        NavStore.pendingAddress = address;
                        NavStore.status = "New watch: pair, verify the system passkey, then approve below.";
                    } else {
                        server.cancelConnection(device);
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (address.equals(NavStore.connectedAddress)) {
                        NavStore.connectedAddress = null;
                        NavStore.status = "Watch disconnected. Reconnect on the watch.";
                    }
                    if (address.equals(NavStore.pendingAddress)) NavStore.pendingAddress = null;
                    if (address.equals(batchAddress)) resetBatch();
                }
            }));
        }

        @Override public void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
                                                         int offset, BluetoothGattCharacteristic characteristic) {
            handler.post(() -> guarded(() -> {
                if (!dataId.equals(characteristic.getUuid())) {
                    respond(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
                    return;
                }
                if (offset != 0) {
                    respond(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null);
                    return;
                }
                String address = device.getAddress();
                if (!NavStore.enabled || !address.equals(trust.approved())
                    || device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    respond(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, 0, null);
                    return;
                }
                long now = android.os.SystemClock.elapsedRealtime();
                if (frames == null || frameIndex == frames.size() || now - batchAt > 5_000
                    || !address.equals(batchAddress)) {
                    frames = PacketCodec.frames(NavStore.snapshot(), sequence++ & 0xffff);
                    frameIndex = 0;
                    batchAt = now;
                    batchAddress = address;
                }
                if (respond(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, frames.get(frameIndex))) {
                    frameIndex++;
                    NavStore.connectedAddress = address;
                    NavStore.pendingAddress = null;
                    NavStore.status = "Encrypted navigation link active";
                }
            }));
        }
    };

    private boolean respond(BluetoothDevice device, int id, int status, int offset, byte[] data) {
        boolean sent = server.sendResponse(device, id, status, offset, data);
        if (!sent) {
            resetBatch();
            NavStore.status = "BLE response failed. Reconnect the watch.";
        }
        return sent;
    }

    private void resetBatch() {
        frames = null;
        frameIndex = 0;
        batchAddress = null;
    }

    private void guarded(Runnable action) {
        if (closed) return;
        try {
            action.run();
        } catch (SecurityException exception) {
            fatal.accept("Bluetooth permission was revoked. Grant Nearby devices access and restart.");
        } catch (IllegalStateException exception) {
            fatal.accept("Bluetooth became unavailable. Enable Bluetooth and restart.");
        }
    }

    @Override public void close() {
        closed = true;
        resetBatch();
        try {
            if (advertiser != null) advertiser.stopAdvertising(advertising);
        } finally {
            if (server != null) server.close();
            server = null;
            NavStore.connectedAddress = null;
            NavStore.pendingAddress = null;
        }
    }
}
