package io.github.vikulin.opengammakit.model

class OpenGammaKitCommands {
    // JSON command protocol (see REQUIREMENTS.md 6.3 and Core/Src/system_controller.c
    // System_HandleCommand()), as opposed to the legacy text commands below.
    fun requestStatus(): String = "{\"type\":\"command\",\"command\":\"request_status\"}\n"

    /**
     * Starts (if not already running) a spectrum measurement and requests an immediate
     * snapshot. durationSeconds=0 means unlimited/"Live" - the device only auto-stops
     * when a positive duration is given ("Schedule" mode); ongoing updates arrive via
     * the device's own periodic spectrum push while measuring, not by re-sending this.
     */
    fun requestSpectrum(durationSeconds: Long = 0): String =
        "{\"type\":\"command\",\"command\":\"request_spectrum\",\"data\":{\"duration_seconds\":$durationSeconds}}\n"

    fun readSpectrum(): String = "read spectrum\n"
    fun readSettings(): String = "read settings\n"
    fun readInfo(): String = "read info\n"
    fun readFs(): String = "read fs\n"
    fun readDir(): String = "read dir\n"
    fun readFile(filename: String): String = "read file $filename\n"
    fun removeFile(filename: String): String = "remove file $filename\n"

    fun setBaseline(toggle: Boolean): String = "set baseline ${toggle.toOnOff()}\n"
    fun setTrng(toggle: Boolean): String = "set trng ${toggle.toOnOff()}\n"
    fun setDisplay(toggle: Boolean): String = "set display ${toggle.toOnOff()}\n"
    fun setMode(mode: String): String = "set mode $mode\n" // geiger or energy
    fun setOut(mode: String): String = "set out $mode\n"   // events, spectrum, off
    fun setAveraging(value: Int): String = "set averaging $value\n"
    fun setTickRate(value: Int): String = "set tickrate $value\n"
    fun setTicker(toggle: Boolean): String = "set ticker ${toggle.toOnOff()}\n"

    fun recordStart(seconds: Long, filename: String): String = "record start $seconds $filename\n"
    fun recordStop(): String = "record stop\n"
    fun recordStatus(): String = "record status\n"

    fun resetSpectrum(): String = "reset spectrum\n"
    fun resetSettings(): String = "reset settings\n"
    fun reboot(): String = "reboot\n"

    private fun Boolean.toOnOff(): String = if (this) "on" else "off"
}