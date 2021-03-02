package com.tans.tfiletransporter.ui.activity.connection

import android.Manifest
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ConnectionActivityBinding
import com.tans.tfiletransporter.net.LOCAL_DEVICE
import com.tans.tfiletransporter.net.connection.launchTcpScanConnectionServer
import com.tans.tfiletransporter.net.connection.launchTopScanConnectionClient
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tans.tfiletransporter.utils.findLocalAddressV4
import com.tans.tfiletransporter.utils.toBytes
import com.tbruyelle.rxpermissions2.RxPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import org.kodein.di.instance
import java.net.InetAddress
import java.util.*

data class ConnectionActivityState(
    val address: Optional<InetAddress> = Optional.empty()
)

class ConnectionActivity : BaseActivity<ConnectionActivityBinding, ConnectionActivityState>(
    layoutId = R.layout.connection_activity,
    defaultState = ConnectionActivityState()
) {

    private val wifiManager: WifiManager by instance()
    private val connectivityManager: ConnectivityManager by instance()

    private val networkRequest: NetworkRequest by lazy {
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
    }

    private val netWorkerCallback: ConnectivityManager.NetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val address = InetAddress.getByAddress(wifiManager.dhcpInfo.ipAddress.toBytes(isRevert = true))
                updateState { it.copy(address = Optional.of(address)) }.bindLife()
            }

            override fun onLost(network: Network) {
                // to deal as hotspot host situation, ugly code.
                launch {
                    updateState {
                        it.copy(address = Optional.empty())
                    }.await()
                    delay(5000)
                    updateState {
                        val canUseAddress = findLocalAddressV4().getOrNull(0)
                        it.copy(address = if (canUseAddress != null) {
                            Optional.of(canUseAddress)
                        } else {
                            Optional.empty()
                        })
                    }.await()
                }
            }
        }
    }

    override fun firstLaunchInitData() {
        launch {
            val grant = RxPermissions(this@ConnectionActivity).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    it.request(Manifest.permission.READ_EXTERNAL_STORAGE)
                } else {
                    it.request(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.firstOrError().await()
            if (!grant) {
                finish()
            }

            updateState {
                // to deal as hotspot host situation, ugly code.
                val canUseAddress = findLocalAddressV4().getOrNull(0)
                it.copy(address = if (canUseAddress != null) {
                    Optional.of(canUseAddress)
                } else {
                    Optional.empty()
                })
            }.await()
        }
//
//        launch(Dispatchers.IO) {
//            val localAddress = bindState().filter { it.address.isPresent }.map { it.address.get() }.firstOrError().await()
//            launch {
//                launchTopScanConnectionClient(
//                        localAddress = localAddress,
//                        localDevice = LOCAL_DEVICE
//                ) {
//                    bindRemoteDevice()
//                            .map {
//                                println("Devices: $it")
//                            }
//                            .bindLife()
//                }
//            }
//            launch {
//                launchTcpScanConnectionServer(
//                        localAddress = localAddress,
//                        localDevice = LOCAL_DEVICE
//                ) { address, info ->
//                    false
//                }
//            }
//        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager.registerNetworkCallback(networkRequest, netWorkerCallback)
    }

    override fun initViews(binding: ConnectionActivityBinding) {
        binding.deviceTv.text = getString(R.string.broadcast_connection_local_device, LOCAL_DEVICE)
        render ({ it.address }) {
            binding.ipAddressTv.text = getString(R.string.broadcast_connection_local_ip_address, if (it.isPresent) it.get().hostAddress else "Not available")
        }.bindLife()
    }

    override fun onDestroy() {
        connectivityManager.unregisterNetworkCallback(netWorkerCallback)
        super.onDestroy()
    }

}