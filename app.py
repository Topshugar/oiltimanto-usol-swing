from flask import Flask, jsonify
from flask_cors import CORS
import requests
from datetime import datetime, timedelta
import math

app = Flask(__name__)
CORS(app)

def get_oil_price():
    try:
        # Yahoo Finance CL=F
        r = requests.get("https://query1.finance.yahoo.com/v8/finance/chart/CL=F?interval=5m&range=1d", headers={"User-Agent":"Mozilla"}, timeout=10)
        data = r.json()
        result = data['chart']['result'][0]
        price = result['meta']['regularMarketPrice']
        closes = result['indicators']['quote'][0]['close']
        closes = [x for x in closes if x is not None]
        return price, closes
    except:
        return 78.50, [78.0,78.2,78.5]

def get_dxy():
    try:
        r = requests.get("https://query1.finance.yahoo.com/v8/finance/chart/DX-Y.NYB?interval=5m&range=1d", headers={"User-Agent":"Mozilla"}, timeout=10)
        data = r.json()
        return data['chart']['result'][0]['meta']['regularMarketPrice']
    except:
        return 103.5

def calc_ema(prices, period):
    if len(prices) < period: return prices[-1]
    k = 2/(period+1)
    ema = sum(prices[:period])/period
    for p in prices[period:]:
        ema = p*k + ema*(1-k)
    return ema

def calc_rsi(prices, period=14):
    if len(prices) <= period: return 50
    gains=losses=0
    for i in range(1, period+1):
        diff = prices[-i] - prices[-i-1]
        if diff>0: gains+=diff
        else: losses+=abs(diff)
    if losses==0: return 100
    rs=gains/losses
    return 100-(100/(1+rs))

def next_eia():
    # EIA is every Wednesday 10:30 AM ET -> 15:30 UTC
    now = datetime.utcnow()
    days_ahead = (2 - now.weekday()) % 7  # Wednesday = 2
    next_wed = now + timedelta(days=days_ahead)
    next_wed = next_wed.replace(hour=15, minute=30, second=0, microsecond=0)
    if next_wed < now:
        next_wed += timedelta(days=7)
    diff = next_wed - now
    hours = diff.total_seconds()/3600
    return next_wed.isoformat(), hours

@app.route('/')
def home():
    return jsonify({"message":"OILTIMANTO IS COMING TO BREAK THE MATRIX", "status":"LIVE"})

@app.route('/api/signal')
def signal():
    price, closes = get_oil_price()
    dxy = get_dxy()
    ema9 = calc_ema(closes, 9)
    ema21 = calc_ema(closes, 21)
    rsi = calc_rsi(closes)
    eia_time, eia_hours = next_eia()

    # DXY FILTER
    dxy_trend = "BEARISH (Good for Oil BUY)" if dxy < 104 else "BULLISH (Bad for Oil)"
    dxy_filter = dxy < 104.5  # Allow BUY only if DXY not too strong

    # EIA BLOCK
    eia_block = eia_hours < 2 and eia_hours > -1  # 2h before to 1h after

    # CORE SIGNAL
    if eia_block:
        trend = "NO TRADE"
        signal_text = "NO TRADE - EIA INCOMING"
        reason = f"EIA in {int(eia_hours)}h - Oiltimanto says WAIT"
    elif ema9 > ema21 and rsi < 70 and dxy_filter:
        trend = "LONG"
        signal_text = "BUY / LONG"
        reason = f"EMA9 {ema9:.2f} > EMA21 {ema21:.2f} + RSI {rsi:.1f} + DXY {dxy:.2f}"
    elif ema9 < ema21 and rsi > 30:
        trend = "SHORT"
        signal_text = "SELL / SHORT"
        reason = f"EMA9 {ema9:.2f} < EMA21 {ema21:.2f} + RSI {rsi:.1f}"
    else:
        trend = "NO TRADE"
        signal_text = "NO TRADE / SIDEWAYS"
        reason = f"RSI {rsi:.1f} neutral, waiting for breakout"

    sl = price*0.992 if trend=="LONG" else price*1.008 if trend=="SHORT" else 0
    tp = price*1.018 if trend=="LONG" else price*0.982 if trend=="SHORT" else 0

    return jsonify({
        "oil_price": round(price,2),
        "dxy": round(dxy,2),
        "dxy_status": dxy_trend,
        "ema9": round(ema9,2),
        "ema21": round(ema21,2),
        "rsi": round(rsi,1),
        "trend": trend,
        "signal": signal_text,
        "reason": reason,
        "entry": round(price,2),
        "sl": round(sl,2),
        "tp": round(tp,2),
        "eia_next": eia_time,
        "eia_hours_left": round(eia_hours,1),
        "eia_block": eia_block,
        "timestamp": datetime.utcnow().isoformat(),
        "source": "Yahoo Finance CL=F + DX-Y.NYB"
    })

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=10000)