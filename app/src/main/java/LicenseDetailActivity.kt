package com.example.driverlicensescanner

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class LicenseDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_detail)

        val licenseInfo = intent.getStringExtra("LICENSE_INFO") ?: "No license information available"
        findViewById<TextView>(R.id.license_info_text_view).text = licenseInfo
    }
}
