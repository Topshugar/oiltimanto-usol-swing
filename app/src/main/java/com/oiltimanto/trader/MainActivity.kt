package com.oiltimanto.trader

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.URL
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var priceText: TextView
    private lateinit var signalText: TextView
    private lateinit var dxyText: TextView
    private lateinit var entryText: TextView
    private lateinit var slText: TextView
    private lateinit var tpText: TextView
    private lateinit var eiaText: TextView
    private lateinit var timeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        priceText = findViewById(R.id.priceText)
        signalText = findViewById(R.id.signalText)
        dxyText = findViewById(R.id.dxyText)
        entryText = findViewById(R.id.entryText)
        slText = findViewById(R.id.slText)
        tpText = findViewById(R.id.tpText)
        eiaText = findViewById(R.id.eiaText)
        timeText = findViewById(R.id.timeText)
        val refreshBtn = findViewById<Button>(R.id.refreshBtn)

        refreshBtn.setOnClickListener { fetchAll() }
        fetchAll()
    }

    private fun fetchAll() {
        signalText.text = "OILTIMANTO IS ANALYZING..."
        thread {
            try {
                // 1. USOIL CL=F
                val oilUrl = URL("https://query1.finance.yahoo.com/v8/finance/chart/CL=F?interval=5m&range=1d")
                val oilJson = JSONObject(oilUrl.readText()).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                val oilPrice = oilJson.getJSONObject("meta").getDouble("regularMarketPrice")
                val closes = oilJson.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).getJSONArray("close")
                val prices = mutableListOf<Double>()
                for (i in 0 until closes.length()) { if (!closes.isNull(i)) prices.add(closes.getDouble(i)) }

                // 2. DXY
                var dxyPrice = 103.5
                try {
                    val dxyUrl = URL("https://query1.finance.yahoo.com/v8/finance/chart/DX-Y.NYB?interval=5m&range=1d")
                    val dxyJson = JSONObject(dxyUrl.readText()).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                    dxyPrice = dxyJson.getJSONObject("meta").getDouble("regularMarketPrice")
                } catch (e: Exception) {}

                val ema9 = calcEMA(prices, 9)
                val ema21 = calcEMA(prices, 21)
                val rsi = calcRSI(prices, 14)

                // 3. EIA Countdown - Wednesday 10:30 ET = 15:30 UTC
                val now = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                val nextEia = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                nextEia.set(Calendar.HOUR_OF_DAY, 15); nextEia.set(Calendar.MINUTE, 30); nextEia.set(Calendar.SECOND, 0)
                val daysUntilWed = (Calendar.WEDNESDAY - now.get(Calendar.DAY_OF_WEEK) + 7) % 7
                nextEia.add(Calendar.DAY_OF_YEAR, if (daysUntilWed == 0 && now.get(Calendar.HOUR_OF_DAY) >= 16) 7 else daysUntilWed)
                val diffHours = (nextEia.timeInMillis - now.timeInMillis) / (1000 * 60 * 60).toDouble()
                val eiaBlock = diffHours in -1.0..2.0

                // 4. SIGNAL LOGIC - AGREED SPEC
                val trend: String
                val signal: String
                val reason: String
                val dxyStatus: String

                dxyStatus = if (dxyPrice < 104) "DXY ${String.format("%.2f", dxyPrice)} - BEARISH (Good for OIL)" else "DXY ${String.format("%.2f", dxyPrice)} - BULLISH (Bad for OIL)"

                if (eiaBlock) {
                    trend = "NO TRADE"; signal = "NO TRADE - EIA 🔴"
                    reason = "Oiltimanto says WAIT - EIA in ${String.format("%.1f", diffHours)}h"
                } else if (ema9 > ema21 && rsi < 70 && dxyPrice < 104.5) {
                    trend = "LONG"; signal = "BUY / LONG 🟢"
                    reason = "EMA9 ${String.format("%.2f", ema9)} > EMA21 ${String.format("%.2f", ema21)} | RSI ${String.format("%.1f", rsi)}"
                } else if (ema9 < ema21 && rsi > 30) {
                    trend = "SHORT"; signal = "SELL / SHORT 🔴"
                    reason = "EMA9 ${String.format("%.2f", ema9)} < EMA21 ${String.format("%.2f", ema21)} | RSI ${String.format("%.1f", rsi)}"
                } else {
                    trend = "NO TRADE"; signal = "NO TRADE / SIDEWAYS ⚪"
                    reason = "Market Choppy - RSI ${String.format("%.1f", rsi)}"
                }

                val sl = if (trend == "LONG") oilPrice * 0.992 else if (trend == "SHORT") oilPrice * 1.008 else 0.0
                val tp = if (trend == "LONG") oilPrice * 1.018 else if (trend == "SHORT") oilPrice * 0.982 else 0.0

                runOnUiThread {
                    priceText.text = "USOIL: $${String.format("%.2f", oilPrice)}"
                    signalText.text = signal
                    dxyText.text = dxyStatus
                    entryText.text = if (trend != "NO TRADE") "ENTRY: $${String.format("%.2f", oilPrice)} | $reason" else reason
                    slText.text = if (trend != "NO TRADE") "STOP LOSS: $${String.format("%.2f", sl)}" else ""
                    tpText.text = if (trend != "NO TRADE") "TAKE PROFIT: $${String.format("%.2f", tp)}" else ""
                    eiaText.text = if (eiaBlock) "⚠️ EIA BLOCK ACTIVE - DON'T TRADE" else "EIA Next: Wed 10:30 ET (in ${String.format("%.1f", diffHours)}h) - Safe to Trade"
                    timeText.text = "Updated: ${Date()} | Source: Yahoo CL=F + DXY"
                }
            } catch (e: Exception) {
                runOnUiThread { signalText.text = "ERROR: ${e.message}\nCheck Internet" }
            }
        }
    }

    private fun calcEMA(prices: List<Double>, period: Int): Double {
        if (prices.size < period) return prices.lastOrNull() ?: 0.0
        val k = 2.0 / (period + 1); var ema = prices.take(period).average()
        for (i in period until prices.size) { ema = prices[i] * k + ema * (1 - k) }
        return ema
    }
    private fun calcRSI(prices: List<Double>, period: Int): Double {
        if (prices.size <= period) return 50.0
        var gains = 0.0; var losses = 0.0
        for (i in 1..period) {
            val diff = prices[prices.size - i] - prices[prices.size - i - 1]
            if (diff > 0) gains += diff else losses += abs(diff)
        }
        if (losses == 0.0) return 100.0
        val rs = gains / losses; return 100 - (100 / (1 + rs))
    }
}