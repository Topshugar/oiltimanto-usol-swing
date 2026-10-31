package com.oiltimanto.trader

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.floor

class MainActivity : AppCompatActivity() {

    private lateinit var equityInput: EditText
    private lateinit var riskInput: EditText
    private lateinit var entryInput: EditText
    private lateinit var slInput: EditText
    private lateinit var resultText: TextView
    private lateinit var h1Light: TextView
    private lateinit var h4Light: TextView
    private lateinit var d1Light: TextView
    private lateinit var w1Light: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        equityInput = findViewById(R.id.equityInput)
        riskInput = findViewById(R.id.riskInput)
        entryInput = findViewById(R.id.entryInput)
        slInput = findViewById(R.id.slInput)
        resultText = findViewById(R.id.resultText)
        h1Light = findViewById(R.id.h1Light)
        h4Light = findViewById(R.id.h4Light)
        d1Light = findViewById(R.id.d1Light)
        w1Light = findViewById(R.id.w1Light)

        val calculateBtn: Button = findViewById(R.id.calculateBtn)
        val checkMTFBtn: Button = findViewById(R.id.checkMTFBtn)

        calculateBtn.setOnClickListener { calculateLotSize() }
        checkMTFBtn.setOnClickListener { checkMTFConfluence() }
    }

    // OILTIMANTO LOT CALCULATOR FOR USOIL
    private fun calculateLotSize() {
        try {
            val equity = equityInput.text.toString().toDouble()
            val riskPercent = riskInput.text.toString().toDouble()
            val entry = entryInput.text.toString().toDouble()
            val sl = slInput.text.toString().toDouble()

            if (equity <= 0 || riskPercent <= 0) {
                resultText.text = "Equity and Risk must be > 0"
                return
            }

            val riskAmount = equity * (riskPercent / 100.0)
            val slDistance = kotlin.math.abs(entry - sl)

            if (slDistance == 0.0) {
                resultText.text = "SL distance cannot be 0"
                return
            }

            // USOIL: 1.00 lot = $10 per $1 move (standard)
            // Formula: Lot = RiskAmount / (SL distance * 10 * contract size factor)
            // Using simplified: Lot = RiskAmount / (SL_Distance * 10)
            var lotSize = riskAmount / (slDistance * 10.0)
            
            // Round down to 2 decimals for safety
            lotSize = floor(lotSize * 100) / 100.0
            if (lotSize < 0.01) lotSize = 0.01

            resultText.text = "LOT SIZE: $lotSize\nRisk: $${String.format("%.2f", riskAmount)}\nSL: $slDistance $"

        } catch (e: Exception) {
            resultText.text = "Fill all fields correctly: ${e.message}"
        }
    }

    // MTF SWING ENGINE LIGHTS
    private fun checkMTFConfluence() {
        // For now manual toggle - later connect to TradingView API
        // Green = Bullish Confluence, Red = Bearish, Yellow = Wait
        
        // Example logic: you can edit these conditions
        setLight(h1Light, "H1", true)   // true = green bullish
        setLight(h4Light, "H4", true)
        setLight(d1Light, "D1", false)  // false = red bearish
        setLight(w1Light, "W1", true)

        Toast.makeText(this, "MTF Checked: 3/4 Bullish", Toast.LENGTH_SHORT).show()
    }

    private fun setLight(view: TextView, label: String, bullish: Boolean) {
        if (bullish) {
            view.text = "$label ● BULL"
            view.setBackgroundColor(android.graphics.Color.parseColor("#22C55E"))
        } else {
            view.text = "$label ● BEAR"
            view.setBackgroundColor(android.graphics.Color.parseColor("#EF4444"))
        }
    }
}