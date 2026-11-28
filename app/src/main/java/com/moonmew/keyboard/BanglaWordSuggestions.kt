package com.moonmew.keyboard

/**
 * Small on-device Bangla starter lexicon. It is intentionally stored locally:
 * no network requests and no typed text leaves the device.
 */
object BanglaWordSuggestions {
    private val words = linkedMapOf(
        "ami" to "আমি", "amar" to "আমার", "amra" to "আমরা", "amake" to "আমাকে",
        "tumi" to "তুমি", "tomar" to "তোমার", "tomake" to "তোমাকে", "apni" to "আপনি",
        "apnar" to "আপনার", "se" to "সে", "she" to "সে", "tara" to "তারা",
        "amader" to "আমাদের", "tomader" to "তোমাদের", "ki" to "কি", "keno" to "কেন",
        "kothay" to "কোথায়", "kemon" to "কেমন", "kobe" to "কবে", "ke" to "কে",
        "kirokom" to "কীরকম", "hoy" to "হয়", "hoye" to "হয়ে",
        "ache" to "আছে", "achi" to "আছি", "achhi" to "আছি", "nei" to "নেই",
        "na" to "না", "hya" to "হ্যাঁ", "ha" to "হ্যাঁ", "bhalo" to "ভালো",
        "kharap" to "খারাপ", "sundor" to "সুন্দর", "onek" to "অনেক",
        "khub" to "খুব", "beshi" to "বেশি", "kom" to "কম", "ekta" to "একটা",
        "ekhon" to "এখন", "aj" to "আজ", "kal" to "কাল", "gotokal" to "গতকাল",
        "agami" to "আগামী", "din" to "দিন", "raat" to "রাত", "sokal" to "সকাল",
        "dupur" to "দুপুর", "somoy" to "সময়", "shomoy" to "সময়",
        "bangla" to "বাংলা", "bangladesh" to "বাংলাদেশ", "desh" to "দেশ", "bhasha" to "ভাষা",
        "manush" to "মানুষ", "bondhu" to "বন্ধু", "poribar" to "পরিবার", "ma" to "মা",
        "baba" to "বাবা", "bhai" to "ভাই", "bon" to "বোন", "dada" to "দাদা",
        "didi" to "দিদি", "dhonnobad" to "ধন্যবাদ", "shagotom" to "স্বাগতম",
        "please" to "প্লিজ", "sorry" to "সরি", "bujhi" to "বুঝি", "bujhte" to "বুঝতে",
        "chai" to "চাই", "chao" to "চাও", "korbo" to "করব", "koro" to "করো",
        "korchi" to "করছি", "kore" to "করে", "kora" to "করা", "korechi" to "করেছি",
        "jacchi" to "যাচ্ছি", "jabo" to "যাব", "dao" to "দাও", "dibe" to "দেবে",
        "hobe" to "হবে", "pari" to "পারি", "parina" to "পারি না",
        "okhane" to "ওখানে", "bari" to "বাড়ি", "kaj" to "কাজ",
        "school" to "স্কুল", "phone" to "ফোন", "keyboard" to "কিবোর্ড", "app" to "অ্যাপ",
        "computer" to "কম্পিউটার", "shikkha" to "শিক্ষা", "porashona" to "পড়াশোনা",
        "jibon" to "জীবন", "shopno" to "স্বপ্ন", "mon" to "মন", "bhalobasha" to "ভালোবাসা",
        "valobasha" to "ভালোবাসা", "khushi" to "খুশি", "dukkho" to "দুঃখ",
        "allah" to "আল্লাহ", "inshallah" to "ইনশাআল্লাহ", "mashallah" to "মাশাআল্লাহ",
        "shukriya" to "শুকরিয়া", "assalamu" to "আসসালামু", "alaikum" to "আলাইকুম",
        // Everyday words and short conversational phrases.
        "tahole" to "তাহলে", "taholei" to "তাহলেই", "kintu" to "কিন্তু",
        "karon" to "কারণ", "jodi" to "যদি", "jokhon" to "যখন", "tokhon" to "তখন",
        "ekhono" to "এখনো", "shob" to "সব", "sob" to "সব", "shobai" to "সবাই",
        "sobai" to "সবাই", "kichu" to "কিছু", "kichui" to "কিছুই", "shudhu" to "শুধু",
        "sudhu" to "শুধু", "aro" to "আরও", "abar" to "আবার", "ekhonkar" to "এখনকার",
        "theke" to "থেকে", "jonno" to "জন্য", "sathe" to "সাথে", "shonge" to "সঙ্গে",
        "moddhe" to "মধ্যে", "upore" to "উপরে", "niche" to "নিচে", "samne" to "সামনে",
        "pore" to "পরে", "age" to "আগে", "ekhane" to "এখানে", "sekhan" to "সেখানে",
        "kothao" to "কোথাও", "kono" to "কোনো", "kon" to "কোন", "keu" to "কেউ",
        "kichuna" to "কিছু না", "hoyni" to "হয়নি", "mone" to "মনে",
        "monehoy" to "মনে হয়", "janish" to "জানিস", "jani" to "জানি", "janina" to "জানি না",
        "bujhlam" to "বুঝলাম", "bujhina" to "বুঝি না", "bol" to "বল", "bolo" to "বলো",
        "bolchi" to "বলছি", "bolbo" to "বলব", "shono" to "শোনো", "dekho" to "দেখো",
        "dekhi" to "দেখি", "dekha" to "দেখা", "likho" to "লিখো", "likhbo" to "লিখব",
        "pori" to "পড়ি", "khabo" to "খাব", "kheyechi" to "খেয়েছি", "pani" to "পানি",
        "khabar" to "খাবার", "bhat" to "ভাত", "ghum" to "ঘুম", "ghumabo" to "ঘুমাব",
        "shundor" to "সুন্দর", "valo" to "ভালো", "darun" to "দারুণ", "moja" to "মজা",
        "thik" to "ঠিক", "thikache" to "ঠিক আছে", "thikase" to "ঠিক আছে",
        "hmm" to "হুম", "accha" to "আচ্ছা", "acha" to "আচ্ছা", "oboshshoi" to "অবশ্যই",
        "dorkar" to "দরকার", "lagbe" to "লাগবে", "lagena" to "লাগে না", "parbe" to "পারবে",
        "parbo" to "পারব", "korben" to "করবেন", "korbenna" to "করবেন না",
        "asbo" to "আসব", "ashbo" to "আসব", "gechi" to "গেছি", "gecho" to "গেছো",
        "bosh" to "বস", "bosho" to "বসো", "uthho" to "উঠো", "cholo" to "চলো",
        "chol" to "চল", "dhire" to "ধীরে", "taratari" to "তাড়াতাড়ি",
        "ekshathe" to "একসাথে", "ekshathei" to "একসাথেই", "shotti" to "সত্যি",
        "shotyi" to "সত্যি", "mittha" to "মিথ্যা", "shomossa" to "সমস্যা",
        "somossa" to "সমস্যা", "shomadhan" to "সমাধান", "uttor" to "উত্তর",
        "proshno" to "প্রশ্ন", "bikel" to "বিকেল", "rat" to "রাত", "shokal" to "সকাল",
        // More everyday conversation, polite forms and common mobile/computing terms.
        "apnara" to "আপনারা", "apnader" to "আপনাদের", "oder" to "ওদের", "ora" to "ওরা",
        "tora" to "তোরা", "toder" to "তোদের", "amaderke" to "আমাদেরকে", "tomaderke" to "তোমাদেরকে",
        "ekhankar" to "এখানকার", "sekhaney" to "সেখানে", "okhankar" to "ওখানকার",
        "kothaycho" to "কোথায়ছো", "kothayachen" to "কোথায় আছেন",
        "ashole" to "আসলে", "asole" to "আসলে", "monekor" to "মনে কর",
        "bhabchi" to "ভাবছি", "vabchi" to "ভাবছি", "bhabna" to "ভাবনা",
        "chinta" to "চিন্তা", "chinta koro" to "চিন্তা করো", "oshubidha" to "অসুবিধা",
        "subidha" to "সুবিধা", "shoja" to "সোজা", "kothin" to "কঠিন",
        "shuru" to "শুরু", "shesh" to "শেষ", "sheshhoyeche" to "শেষ হয়েছে",
        "proyojon" to "প্রয়োজন", "proyojone" to "প্রয়োজনে", "shombhob" to "সম্ভব",
        "somvob" to "সম্ভব", "oshombhob" to "অসম্ভব", "nishchoi" to "নিশ্চয়ই",
        "nischoy" to "নিশ্চয়", "hoyto" to "হয়তো", "monehochhe" to "মনে হচ্ছে",
        "bhalo lage" to "ভালো লাগে", "bhalo lagche" to "ভালো লাগছে",
        "valo lagche" to "ভালো লাগছে", "khub bhalo" to "খুব ভালো",
        "onek dhonnobad" to "অনেক ধন্যবাদ", "shubho" to "শুভ", "shuvo" to "শুভ",
        "shubhoratri" to "শুভরাত্রি", "shubhosokal" to "শুভ সকাল",
        "message" to "মেসেজ", "msg" to "মেসেজ", "chat" to "চ্যাট", "internet" to "ইন্টারনেট",
        "online" to "অনলাইন", "offline" to "অফলাইন", "download" to "ডাউনলোড",
        "upload" to "আপলোড", "file" to "ফাইল", "photo" to "ছবি", "chobi" to "ছবি",
        "video" to "ভিডিও", "link" to "লিংক", "website" to "ওয়েবসাইট",
        "password" to "পাসওয়ার্ড", "account" to "অ্যাকাউন্ট", "settings" to "সেটিংস",
        "update" to "আপডেট", "install" to "ইনস্টল", "delete" to "ডিলিট",
        "save" to "সেভ", "share" to "শেয়ার", "search" to "সার্চ",
        "typing" to "টাইপিং", "lekha" to "লেখা", "likhte" to "লিখতে",
        "poriborton" to "পরিবর্তন", "shundor kore" to "সুন্দর করে",
        "ekhoni" to "এখনই", "akhoni" to "এখনই", "porey" to "পরে",
        "kalke" to "কালকে", "ajke" to "আজকে", "prottek" to "প্রত্যেক",
        "protidin" to "প্রতিদিন", "shobshomoy" to "সবসময়", "shobsomoy" to "সবসময়"
    )

    /** Exact dictionary lookup, tolerant of accidental surrounding spaces/case. */
    fun exact(roman: String): String? = words[roman.trim().lowercase()]

    // Build the sorted key index once. The old implementation scanned and sorted
    // the entire dictionary on every keystroke, which made phonetic typing lag.
    private val sortedWordEntries by lazy(LazyThreadSafetyMode.PUBLICATION) {
        words.entries.sortedBy { it.key }
    }

    /** Returns a deterministic short list using a binary-searched prefix range. */
    fun suggest(prefix: String, limit: Int = 3): List<String> {
        val query = prefix.trim().lowercase()
        val safeLimit = limit.coerceIn(0, 5)
        if (query.isBlank() || safeLimit == 0) return emptyList()

        val entries = sortedWordEntries
        var low = 0
        var high = entries.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (entries[mid].key < query) low = mid + 1 else high = mid
        }

        val matches = ArrayList<Map.Entry<String, String>>(16)
        var index = low
        while (index < entries.size) {
            val entry = entries[index]
            if (!entry.key.startsWith(query)) break
            matches.add(entry)
            index++
        }
        matches.sortWith(
            compareByDescending<Map.Entry<String, String>> { it.key == query }
                .thenBy { it.key.length }
                .thenBy { it.key }
        )
        val results = ArrayList<String>(safeLimit)
        val seen = HashSet<String>(safeLimit)
        for (entry in matches) {
            if (seen.add(entry.value)) results.add(entry.value)
            if (results.size == safeLimit) break
        }
        return results
    }
}
