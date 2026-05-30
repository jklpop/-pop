package com.example.data

data class Surah(
    val number: Int,
    val nameEnglish: String,
    val nameArabic: String,
    val ayahCount: Int
)

data class Reciter(
    val id: String,
    val displayNameArabic: String,
    val displayNameEnglish: String
)

object QuranData {
    val RECITERS = listOf(
        Reciter("AbdulSamad_64kbps_QuranExplorer.Com", "عبدالباسط عبدالصمد (مجود)", "Abdul Basit (Mujawwad)"),
        Reciter("Abdul_Basit_Murattal_64kbps", "عبدالباسط عبدالصمد (مرتل)", "Abdul Basit (Murattal)"),
        Reciter("Abdurrahmaan_As-Sudais_64kbps", "عبدالرحمن السديس", "Abdurrahman As-Sudais"),
        Reciter("Maher_AlMuaiqly_64kbps", "ماهر المعيقلي", "Maher Al-Muaiqly"),
        Reciter("Alafasy_64kbps", "مشاري العفاسي", "Mishary Alafasy"),
        Reciter("Husary_64kbps", "محمود خليل الحصري", "Mahmoud Khalil Al-Husary"),
        Reciter("Hudhaify_64kbps", "عبدالله الحذيفي", "Ali Al-Huthaify"),
        Reciter("AbdulRahman_Mossad", "عبدالرحمن مسعد", "Abdul Rahman Mossad"),
        Reciter("Abdul_Rahman_Al3ossy_64kbps", "عبدالرحمن العوسي", "Abdul Rahman Al-Ousi")
    )

    private val VERSE_COUNTS = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6
    )

    val SURAHS = listOf(
        "الفاتحة", "البقرة", "آل عمران", "النساء", "المائدة", "الأنعام", "الأعراف", "الأنفال", "التوبة", "يونس",
        "هود", "يوسف", "الرعد", "إبراهيم", "الحجر", "النحل", "الإسراء", "الكهف", "مريم", "طه",
        "الأنبياء", "الحج", "المؤمنون", "النور", "الفرقان", "الشعراء", "النمل", "القصص", "العنكبوت", "الروم",
        "لقمان", "السجدة", "الأحزاب", "سبأ", "فاطر", "يس", "الصافات", "ص", "الزمر", "غافر",
        "فصلت", "الشورى", "الزخرف", "الدخان", "الجاثية", "الأحقاف", "محمد", "الفتح", "الحجرات", "ق",
        "الذاريات", "الطور", "النجم", "القمر", "الرحمن", "الواقعة", "الحديد", "المجادلة", "الحشر", "الممتحنة",
        "الصف", "الجمعة", "المنافقون", "التغابن", "الطلاق", "التحريم", "الملك", "القلم", "الحاقة", "المعارج",
        "نوح", "الجن", "المزمل", "المدثر", "القيامة", "الإنسان", "المرسلات", "النبأ", "النازعات", "عبس",
        "التكوير", "الانفطار", "المطففين", "الانشقاق", "البروج", "الطارق", "الأعلى", "الغاشية", "الفجر", "البلد",
        "الشمس", "الليل", "الضحى", "الشرح", "التين", "العلق", "القدر", "البينة", "الزلزلة", "العاديات",
        "القارعة", "التكاثر", "العصر", "الهمزة", "الفيل", "قريش", "الماعون", "الكوثر", "الكافرون", "النصر",
        "المسد", "الإخلاص", "الفلق", "الناس"
    ).mapIndexed { index, name ->
        Surah(
            number = index + 1,
            nameEnglish = getEnglishName(index + 1),
            nameArabic = name,
            ayahCount = VERSE_COUNTS[index]
        )
    }

    fun getSurah(number: Int): Surah? {
        if (number in 1..114) {
            return SURAHS[number - 1]
        }
        return null
    }

    private fun getEnglishName(num: Int): String {
        return when (num) {
            1 -> "Al-Fatihah"
            2 -> "Al-Baqarah"
            3 -> "Ali 'Imran"
            4 -> "An-Nisa"
            5 -> "Al-Ma'idah"
            6 -> "Al-An'am"
            7 -> "Al-A'raf"
            8 -> "Al-Anfal"
            9 -> "At-Tawbah"
            10 -> "Yunus"
            11 -> "Hud"
            12 -> "Yusuf"
            13 -> "Ar-Ra'd"
            14 -> "Ibrahim"
            15 -> "Al-Hijr"
            16 -> "An-Nahl"
            17 -> "Al-Isra"
            18 -> "Al-Kahf"
            19 -> "Maryam"
            20 -> "Taha"
            21 -> "Al-Anbya"
            22 -> "Al-Hajj"
            23 -> "Al-Mu'minun"
            24 -> "An-Nur"
            25 -> "Al-Furqan"
            26 -> "Ash-Shu'ara"
            27 -> "An-Naml"
            28 -> "Al-Qasas"
            29 -> "Al-Ankabut"
            30 -> "Ar-Rum"
            31 -> "Luqman"
            32 -> "As-Sajdah"
            33 -> "Al-Ahzab"
            34 -> "Saba"
            35 -> "Fatir"
            36 -> "Ya-Sin"
            37 -> "As-Saffat"
            38 -> "Sad"
            39 -> "Az-Zumar"
            40 -> "Ghafir"
            41 -> "Fussilat"
            42 -> "Ash-Shura"
            43 -> "Az-Zukhruf"
            44 -> "Ad-Dukhan"
            45 -> "Al-Jathiyah"
            46 -> "Al-Ahqaf"
            47 -> "Muhammad"
            48 -> "Al-Fath"
            49 -> "Al-Hujurat"
            50 -> "Qaf"
            51 -> "Ad-Dhariyat"
            52 -> "At-Tur"
            53 -> "An-Najm"
            54 -> "Al-Qamar"
            55 -> "Ar-Rahman"
            56 -> "Al-Waqi'ah"
            57 -> "Al-Hadid"
            58 -> "Al-Mujadilah"
            59 -> "Al-Hashr"
            60 -> "Al-Mumtahanah"
            61 -> "As-Saff"
            62 -> "Al-Jumu'ah"
            63 -> "Al-Munafiqun"
            64 -> "At-Taghabun"
            65 -> "At-Talaq"
            66 -> "At-Tahrim"
            67 -> "Al-Mulk"
            68 -> "Al-Qalam"
            69 -> "Al-Haqqah"
            70 -> "Al-Ma'arij"
            71 -> "Nuh"
            72 -> "Al-Jinn"
            73 -> "Al-Muzzammil"
            74 -> "Al-Muddaththir"
            75 -> "Al-Qiyamah"
            76 -> "Al-Insan"
            77 -> "Al-Mursalat"
            78 -> "An-Naba"
            79 -> "An-Naziat"
            80 -> "Abasa"
            81 -> "At-Takwir"
            82 -> "Al-Infitar"
            83 -> "Al-Mutaffifin"
            84 -> "Al-Inshiqaq"
            85 -> "Al-Buruj"
            86 -> "At-Tariq"
            87 -> "Al-A'la"
            88 -> "Al-Ghashiyah"
            89 -> "Al-Fajr"
            90 -> "Al-Balad"
            91 -> "Ash-Shams"
            92 -> "Al-Layl"
            93 -> "Ad-Duha"
            94 -> "Ash-Sharh"
            95 -> "At-Tin"
            96 -> "Al-Alaq"
            97 -> "Al-Qadr"
            98 -> "Al-Bayyinah"
            99 -> "Az-Zalzalah"
            100 -> "Al-Adiyat"
            101 -> "Al-Qari'ah"
            102 -> "At-Takathur"
            103 -> "Al-Asr"
            104 -> "Al-Humazah"
            105 -> "Al-Fil"
            106 -> "Quraysh"
            107 -> "Al-Ma'un"
            108 -> "Al-Kauthar"
            109 -> "Al-Kafirun"
            110 -> "An-Nasr"
            111 -> "Al-Masad"
            112 -> "Al-Ikhlas"
            113 -> "Al-Falaq"
            114 -> "An-Nas"
            else -> "Surah $num"
        }
    }
}
