package com.tans.tfiletransporter.ui.activity.connection

import android.net.*
import android.net.wifi.WifiManager
import com.jakewharton.rxbinding3.view.clicks
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.BroadcastConnectionFragmentBinding
import com.tans.tfiletransporter.net.LOCAL_DEVICE
import com.tans.tfiletransporter.ui.activity.BaseFragment
import com.tans.tfiletransporter.ui.activity.filetransport.activity.FileTransportActivity
import com.tans.tfiletransporter.utils.*
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.Single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import org.kodein.di.instance
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.util.*

class BroadcastConnectionFragment : BaseFragment<BroadcastConnectionFragmentBinding, Optional<InetAddress>>(
    layoutId = R.layout.broadcast_connection_fragment,
    default = Optional.empty()
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
                updateState { Optional.of(address) }.bindLife()
            }

            override fun onLost(network: Network) {
                // to deal as hotspot host situation, ugly code.
                launch {
                    updateState {
                        Optional.empty()

                    }.await()
                    delay(5000)
                    updateState {
                        val canUseAddress = findLocalAddressV4().getOrNull(0)
                        if (canUseAddress != null) {
                            Optional.of(canUseAddress)
                        } else {
                            Optional.empty()
                        }
                    }.await()
                }
            }
        }
    }

    override fun initViews(binding: BroadcastConnectionFragmentBinding) {

        updateState {
            // to deal as hotspot host situation, ugly code.
            val canUseAddress = findLocalAddressV4().getOrNull(0)
            if (canUseAddress != null) {
                Optional.of(canUseAddress)
            } else {
                Optional.empty()
            }
        }.bindLife()

//        launch(Dispatchers.IO) {
//            val localAddress = bindState().firstOrError().filter { it.isPresent }.map { it.get() }.await()
//            val (broadcast, sub) = localAddress!!.getBroadcastAddress()
//            println("Broadcast: ${broadcast.hostAddress}, Sub: $sub")
//            val dc = openDatagramChannel()
//            dc.setOptionSuspend(StandardSocketOptions.SO_REUSEADDR, true)
//            dc.bindSuspend(InetSocketAddress(localAddress, 9999))
//            val buffer = ByteBuffer.allocate(1024)
//            while (true) {
//                buffer.clear()
//                dc.receiveSuspend(buffer)
//                buffer.flip()
//                println("Remote Message Size: ${buffer.limit()}")
//            }
//        }

        connectivityManager.registerNetworkCallback(networkRequest, netWorkerCallback)


        binding.deviceTv.text = getString(R.string.broadcast_connection_local_device, LOCAL_DEVICE)

        render {
            binding.ipAddressTv.text = getString(R.string.broadcast_connection_local_ip_address, if (it.isPresent) it.get().hostAddress else "Not available")
        }.bindLife()

        binding.searchServerLayout.clicks()
                .withLatestFrom(bindState())
                .filter { it.second.isPresent }
                .map { it.second.get() }
                .switchMapSingle { localAddress ->
                    requireActivity().showBroadcastReceiverDialog(localAddress, true)
                            .doOnSuccess {
                                if (it.isPresent) {
                                    startActivity(FileTransportActivity.getIntent(
                                            context = requireContext(),
                                            localAddress = localAddress,
                                            remoteDevice = it.get(),
                                            asServer = false))
                                }
                            }
                            .map {  }
                            .onErrorResumeNext {
                                Single.just(Unit)
                            }
                }
                .bindLife()

        binding.asServerLayout.clicks()
                .withLatestFrom(bindState())
                .filter { it.second.isPresent }
                .map { it.second.get() }
                .switchMapSingle { localAddress ->
                    requireActivity().showBroadcastSenderDialog(localAddress, true)
                            .doOnSuccess {
                                if (it.isPresent) {
                                    startActivity(FileTransportActivity.getIntent(
                                            context = requireContext(),
                                            localAddress = localAddress,
                                            remoteDevice = it.get(),
                                            asServer = true))
                                }
                            }
                            .map {  }
                            .onErrorResumeNext {
                                Single.just(Unit)
                            }
                }
                .bindLife()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        connectivityManager.unregisterNetworkCallback(netWorkerCallback)
    }

}