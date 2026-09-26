package com.flux.app.ml

/**
 * Built-in labeled corpus seeded into training_samples on first launch, so the
 * Naive Bayes model is useful before the user has made a single correction.
 * Realistic notification phrasing; the tokenizer does the rest.
 */
object SeedCorpus {

    val categories: List<Triple<String, String, Long>> = listOf(
        Triple("food_drink", "Food & Drink", 0xFFF97316),
        Triple("transport", "Transport", 0xFF38BDF8),
        Triple("shopping", "Shopping", 0xFFA78BFA),
        Triple("bills_utilities", "Bills & Utilities", 0xFFFACC15),
        Triple("entertainment", "Entertainment", 0xFFF472B6),
        Triple("health", "Health", 0xFF34D399),
        Triple("groceries", "Groceries", 0xFF4ADE80),
        Triple("travel", "Travel", 0xFF60A5FA),
        Triple("income", "Income", 0xFF2DD4BF),
        Triple("transfers", "Transfers & Cash", 0xFF94A3B8),
        Triple("uncategorized", "Uncategorized", 0xFF64748B),
    )

    /** Category id -> fuzzy-matching keywords for Level 1. */
    val keywords: Map<String, List<String>> = mapOf(
        "food_drink" to listOf(
            "swiggy", "zomato", "dominos", "pizza hut", "mcdonald", "kfc", "starbucks",
            "cafe coffee", "barista", "burger king", "subway", "restaurant", "foodpanda",
            "eatfit", "chaayos", "third wave",
        ),
        "transport" to listOf(
            "uber", "ola", "rapido", "metro rail", "irctc", "indian oil", "hp petrol",
            "bharat petrol", "shell petrol", "fuel", "petrol pump", "fastag", "parking",
            "redbus", "nmmt", "park+",
        ),
        "shopping" to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "decathlon",
            "ikea", "croma", "reliance digital", "vijay sales",
        ),
        "bills_utilities" to listOf(
            "electricity", "water bill", "gas bill", "broadband", "airtel", "jio",
            "vodafone", "vi postpaid", "act fibernet", "tatasky", "dish tv", "bill payment",
            "mobile recharge", "prepaid recharge", "postpaid bill", "hathway", "tata play",
        ),
        "entertainment" to listOf(
            "netflix", "spotify", "prime video", "hotstar", "jiocinema", "bookmyshow",
            "pvr cinemas", "inox", "youtube premium", "sony liv", "zee5", "steam games",
        ),
        "health" to listOf(
            "pharmacy", "apollo pharmacy", "medplus", "netmeds", "pharmeasy", "1mg",
            "hospital", "clinic", "diagnostic", "practo", "wellness forever",
        ),
        "groceries" to listOf(
            "bigbasket", "blinkit", "zepto", "instamart", "dmart", "reliance fresh",
            "more supermarket", "grocery", "nature basket", "jiomart",
        ),
        "travel" to listOf(
            "makemytrip", "goibibo", "oyo", "airbnb", "booking.com", "hotel", "flight",
            "indigo airlines", "spicejet", "vistara", "air india", "yatra", "cleartrip",
        ),
        "income" to listOf(
            "salary", "payroll", "interest credit", "dividend", "refund", "cashback",
            "maturity proceeds", "redemption", "payout",
        ),
        "transfers" to listOf(
            "atm withdrawal", "atm cash", "wallet load", "self transfer", "upi transfer",
            "paytm wallet", "added to wallet", "cash deposit", "credit card payment",
            "card payment",
        ),
        "uncategorized" to emptyList(),
    )

    val samples: List<Pair<String, String>> = listOf(
        "Rs 450.00 debited to SWIGGY on 12-09" to "food_drink",
        "Rs 389 spent at ZOMATO LTD via UPI" to "food_drink",
        "Rs 649 paid to DOMINOS PIZZA using card" to "food_drink",
        "INR 220 deducted for MCDONALDS order" to "food_drink",
        "Rs 1120.50 spent at STARBUCKS COFFEE" to "food_drink",
        "Paid Rs 250 to CHAAYOS" to "food_drink",
        "Rs 75 debited towards BURGER KING" to "food_drink",
        "Your card was used for Rs 540 at KFC" to "food_drink",
        "Rs 189.90 debited to UBER INDIA SYSTEMS" to "transport",
        "INR 96 paid to OLA CABS via UPI" to "transport",
        "Rs 45 metro rail recharge" to "transport",
        "Rs 2000 spent at INDIAN OIL PETROL PUMP" to "transport",
        "Fuel purchase of Rs 1500 at HP PETROL" to "transport",
        "Rs 330 debited for FASTAG recharge" to "transport",
        "Rs 850 paid to REDBUS for ticket booking" to "transport",
        "Rs 120 rapido bike ride paid" to "transport",
        "Rs 2499 debited to AMAZON PAY INDIA" to "shopping",
        "INR 1850 spent at FLIPKART INTERNET" to "shopping",
        "Rs 1299 paid to MYNTRA DESIGNS" to "shopping",
        "Rs 899 order confirmed at AJIO" to "shopping",
        "NYKAA order of Rs 745 debited" to "shopping",
        "Rs 4320 spent at CROMA ELECTRONICS" to "shopping",
        "Rs 1250 electricity bill payment" to "bills_utilities",
        "INR 599 paid to AIRTEL POSTPAID bill" to "bills_utilities",
        "Rs 299 jio mobile recharge done" to "bills_utilities",
        "Rs 799 broadband bill payment to ACT FIBERNET" to "bills_utilities",
        "Electricity board bill of Rs 1830 debited" to "bills_utilities",
        "Rs 549 NETFLIX subscription renewed" to "entertainment",
        "INR 119 paid to SPOTIFY INDIA" to "entertainment",
        "Rs 850 booked on BOOKMYSHOW for movie tickets" to "entertainment",
        "Rs 750 paid to PVR CINEMAS" to "entertainment",
        "Rs 499 hotstar subscription debited" to "entertainment",
        "Rs 342 debited at APOLLO PHARMACY" to "health",
        "INR 560 paid to PHARMEASY for medicines" to "health",
        "Rs 1200 hospital consultation fee" to "health",
        "Rs 780 spent at MEDPLUS PHARMACY" to "health",
        "Rs 1245 debited to BIGBASKET" to "groceries",
        "INR 348 paid to BLINKIT grocery order" to "groceries",
        "Rs 512 zepto order debited" to "groceries",
        "Rs 2130 spent at DMART" to "groceries",
        "Rs 8999 paid to MAKEMYTRIP for flight booking" to "travel",
        "INR 2400 OYO ROOMS booking debited" to "travel",
        "Rs 5600 spent at INDIGO AIRLINES" to "travel",
        "Rs 3200 paid to GOIBIBO for hotel" to "travel",
        "Rs 65000 salary credited from ACME CORP" to "income",
        "INR 1200 refund credited to your account" to "income",
        "Rs 85 cashback credited via UPI" to "income",
        "Interest credit of Rs 340.25" to "income",
        "Rs 5000 ATM withdrawal at ANDHERI" to "transfers",
        "INR 1000 wallet load to PAYTM WALLET" to "transfers",
        "Rs 2000 self transfer via UPI" to "transfers",
        "Rs 12500 credit card payment received" to "transfers",
    )
}
