package org.vikulin.opengammakit

class OpenGammaKitCommands {
    fun readSpectrum(): String = "read spectrum"
    fun readSettings(): String = "read settings"
    fun readInfo(): String = "read info"
    fun readFs(): String = "read fs"
    fun readDir(): String = "read dir"
    fun readFile(filename: String): String = "read file $filename"
    fun removeFile(filename: String): String = "remove file $filename"

    fun setBaseline(toggle: Boolean): String = "set baseline ${toggle.toOnOff()}"
    fun setTrng(toggle: Boolean): String = "set trng ${toggle.toOnOff()}"
    fun setDisplay(toggle: Boolean): String = "set display ${toggle.toOnOff()}"
    fun setMode(mode: String): String = "set mode $mode" // geiger or energy
    fun setOut(mode: String): String = "set out $mode"   // events, spectrum, off
    fun setAveraging(value: Int): String = "set averaging $value"
    fun setTickRate(value: Int): String = "set tickrate $value"
    fun setTicker(toggle: Boolean): String = "set ticker ${toggle.toOnOff()}"

    fun recordStart(minutes: Int, filename: String): String = "record start $minutes $filename"
    fun recordStop(): String = "record stop"
    fun recordStatus(): String = "record status"

    fun resetSpectrum(): String = "reset spectrum"
    fun resetSettings(): String = "reset settings"
    fun reboot(): String = "reboot"

    private fun Boolean.toOnOff(): String = if (this) "on" else "off"
}
