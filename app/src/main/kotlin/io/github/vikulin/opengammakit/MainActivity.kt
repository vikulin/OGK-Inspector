package io.github.vikulin.opengammakit

import DevicesFragment
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.FragmentManager

class MainActivity : AppCompatActivity(), FragmentManager.OnBackStackChangedListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportFragmentManager.addOnBackStackChangedListener(this)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment, DevicesFragment(), "devices")
                .commit()
        } else {
            onBackStackChanged()
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
    }

    override fun onBackStackChanged() {
        supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED" == intent.action) {
            val terminal = supportFragmentManager.findFragmentByTag("terminal") as? TerminalFragment
            terminal?.status("USB device detected")
        }
    }
}