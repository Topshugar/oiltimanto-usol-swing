package com.oiltimanto.trader

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var txtH1: TextView
    private lateinit var txtH4: TextView
    private lateinit var txtD1: TextView
    private lateinit var txtW1: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtLotSize: TextView
    private lateinit var txtSignal: TextView
    private lateinit var edtEquity: EditText
    private lateinit var edtRisk: EditText
    private lateinit var edtSL: EditText
    private lateinit var btnCheck: Button

    // Don Oiltimanto MTF Engine
    // Rule: Need 3 out of 4 timeframes to align
    enum class Direction { BUY, SELL, NEUTRAL }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        
        btnCheck.setOnClickListener {
            checkMarket()
        }
        
        // Auto check on open
        checkMarket()
    }

    private fun initViews() {
        txtH1 = findViewById(R.id.txtH1)
        txtH4 = findViewById(R.id.txtH4)
        txtD1 = findViewById(R.id.txtD1)
        txtW1 = findViewById(R.id.txtW1)
        txtStatus = findViewById(R.id.txtStatus)
        txtLotSize = findViewById(R.id.txtLotSize)
        txtSignal = findViewById(R.id.txtSignal)
        edtEquity = findViewById(R.id.edtEquity)
        edtRisk = findViewById(R.id.edtRisk)
        edtSL = findViewById(R.id.edtSL)
        btnCheck = findViewById(R.id.btnCheck)
    }

    private fun checkMarket() {
        val equity = edtEquity.text.toString().toDoubleOrNull() ?: 1000.0
        val riskPercent = edtRisk.text.toString().toDoubleOrNull() ?: 1.0
        val stopLossPoints = edtSL.text.toString().toDoubleOrNull() ?: 200.0 // in points (e.g. 2 USD for USOIL = 200 points)

        // --- MOCK MTF LOGIC ---
        // Replace this with real TwelveData API call later
        // For now, simulating H1,H4,D1,W1 trend detection
        val h1 = getTrend("H1")
        val h4 = getTrend("H4")
        val d1 = getTrend("D1")
        val w1 = getTrend("W1")

        updateLight(txtH1, "H1", h1)
        updateLight(txtH4, "H4", h4)
        updateLight(txtD1, "D1", d1)
        updateLight(txtW1, "W1", w1)

        // Check 3/4 Alignment
        val alignmentResult = checkAlignment(h1, h4, d1, w1)
        
        // Lot Size Calculation: Equity Based
        // Formula: Lot = (Equity * Risk%) / (SL in $ * contract size)
        // For USOIL: 1 lot = 1000 barrels. Price movement 0.01 = $10
        // Simplified: RiskAmount / (SL_points * $0.10)
        val riskAmount = equity * (riskPercent / 100.0)
        // For USOIL: 1.00 price move = $1000 per lot. So if SL is $2.00 = $2000 risk per lot
        // Lot = RiskAmount / SL_dollars / 1000 * 100? Let's use standard formula
        val slDollars = stopLossPoints / 100.0 // convert points to dollars
        val lotSize = if (slDollars > 0) riskAmount / (slDollars * 1000.0) else 0.01
        val finalLot = String.format("%.2f", lotSize.coerceIn(0.01, 10.0))

        txtLotSize.text = "LOT: $finalLot | Risk: $${String.format("%.2f", riskAmount)}"
        txtStatus.text = "Status: ${alignmentResult.status} - ${getTime()}"

        if (alignmentResult.isArmed) {
            txtSignal.text = "🔥 ARMED: ${alignmentResult.direction} on M30 - Lot $finalLot"
            txtSignal.setBackgroundColor(0xFF4CAF50.toInt()) // Green
        } else {
            txtSignal.text = "⏳ WAIT: ${alignmentResult.count}/4 aligned"
            txtSignal.setBackgroundColor(0xFFF44336.toInt()) // Red
        }
    }

    private fun getTrend(tf: String): Direction {
        // TODO: Connect to TwelveData / TradingView API for USOIL
        // EMA 50 vs 200 logic here
        val random = Random().nextInt(3)
        return when(random) {
            0 -> Direction.BUY
            1 -> Direction.SELL
            else -> Direction.NEUTRAL
        }
    }

    private fun updateLight(view: TextView, label: String, dir: Direction) {
        when(dir) {
            Direction.BUY -> {
                view.text = "$label: BUY 🟢"
                view.setBackgroundColor(0xFF4CAF50.toInt())
            }
            Direction.SELL -> {
                view.text = "$label: SELL 🔴"
                view.setBackgroundColor(0xFFF44336.toInt())
            }
            Direction.NEUTRAL -> {
                view.text = "$label: NEUTRAL ⚪"
                view.setBackgroundColor(0xFF9E9E9E.toInt())
            }
        }
    }

    data class AlignmentResult(val isArmed: Boolean, val direction: Direction, val status: String, val count: Int)

    private fun checkAlignment(h1: Direction, h4: Direction, d1: Direction, w1: Direction): AlignmentResult {
        val buys = listOf(h1,h4,d1,w1).count { it == Direction.BUY }
        val sells = listOf(h1,h4,d1,w1).count { it == Direction.SELL }

        return when {
            buys >= 3 -> AlignmentResult(true, Direction.BUY, "3/4 BULLISH ALIGNED", buys)
            sells >= 3 -> AlignmentResult(true, Direction.SELL, "3/4 BEARISH ALIGNED", sells)
            else -> AlignmentResult(false, Direction.NEUTRAL, "NO ALIGNMENT", maxOf(buys, sells))
        }
    }

    private fun getTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
}