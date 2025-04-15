package io.github.vikulin.opengammakit

import android.os.Bundle

class SerialCommandFragment: SerialConnectionFragment() {

    private lateinit var command: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            command = it.getString("command").toString()
        }
    }

    override fun onConnectionSuccess() {
        super.onConnectionSuccess()
        super.setDtr(false)
        super.send(command.toByteArray())
        requireActivity().onBackPressed()
    }

    override fun receive(bytes: ByteArray) {

    }

    override fun status(str: String) {

    }

    override fun onReconnect() {

    }
}