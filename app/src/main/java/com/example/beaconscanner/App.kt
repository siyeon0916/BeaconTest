package com.example.beaconscanner

import android.app.Application
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val beaconManager = BeaconManager.getInstanceForApplication(this)
        beaconManager.beaconParsers.clear()
        // iBeacon
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24")
        )
        // Eddystone-UID
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_UID_LAYOUT)
        )
        // Eddystone-URL
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout(BeaconParser.EDDYSTONE_URL_LAYOUT)
        )
        // AltBeacon
        beaconManager.beaconParsers.add(
            BeaconParser().setBeaconLayout(BeaconParser.ALTBEACON_LAYOUT)
        )
    }
}
