package uz.eslatgich;

import java.util.Random;

/** Ertalabki gapirish mashqi uchun mavzular. */
public final class Topics {
    private Topics() {}

    static final String[][] LIST = {
            {"Describe your perfect weekend.", "Ideal dam olish kunlaringizni tasvirlang."},
            {"What are your plans for today?", "Bugungi rejalaringiz qanday?"},
            {"Talk about your family.", "Oilangiz haqida gapiring."},
            {"Describe your hometown.", "Tug'ilgan shahringizni tasvirlang."},
            {"What is your favourite food and why?", "Sevimli taomingiz qaysi va nega?"},
            {"Talk about a book or film you like.", "Yoqqan kitob yoki film haqida gapiring."},
            {"What did you do yesterday?", "Kecha nima qildingiz?"},
            {"Describe your best friend.", "Eng yaqin do'stingizni tasvirlang."},
            {"Where would you like to travel and why?", "Qayerga sayohat qilishni xohlaysiz va nega?"},
            {"What are your goals for this year?", "Bu yilgi maqsadlaringiz qanday?"},
            {"Talk about your job or studies.", "Ishingiz yoki o'qishingiz haqida gapiring."},
            {"What makes you happy?", "Sizni nima xursand qiladi?"},
            {"Describe a typical day in your life.", "Odatiy kuningizni tasvirlang."},
            {"Is social media good or bad? Explain.", "Ijtimoiy tarmoqlar foydalimi yoki zararli?"},
            {"Talk about a person who inspires you.", "Sizni ilhomlantiradigan inson haqida gapiring."},
            {"What would you do if you won a million dollars?", "Million dollar yutsangiz nima qilardingiz?"},
            {"Describe your favourite season.", "Sevimli faslingizni tasvirlang."},
            {"Why are you learning English?", "Nega ingliz tilini o'rganyapsiz?"},
            {"Talk about a skill you want to learn.", "O'rganmoqchi bo'lgan mahoratingiz haqida."},
            {"Describe a memorable day from your childhood.", "Bolaligingizdagi esda qolarli kun."},
            {"How do you stay healthy?", "Sog'lig'ingizni qanday saqlaysiz?"},
            {"Talk about Uzbek traditions and holidays.", "O'zbek an'analari va bayramlari haqida."},
            {"What is your dream job?", "Orzuingizdagi kasb qanday?"},
            {"Should students wear school uniforms?", "O'quvchilar maktab formasini kiyishi kerakmi?"},
            {"Describe your home.", "Uyingizni tasvirlang."},
            {"Talk about technology in your life.", "Hayotingizdagi texnologiyalar haqida."},
            {"What is the best advice you have received?", "Olgan eng yaxshi maslahatingiz qaysi?"},
            {"Describe a place you love to visit.", "Borishni yaxshi ko'radigan joyingiz."},
            {"City life or village life: which is better?", "Shahar yoki qishloq hayoti: qaysi biri yaxshi?"},
            {"Talk about a problem you solved.", "Hal qilgan muammoingiz haqida gapiring."},
            {"What will your life look like in ten years?", "O'n yildan keyin hayotingiz qanday bo'ladi?"},
            {"Describe your morning routine.", "Ertalabki odatlaringizni tasvirlang."},
    };

    public static int random(int except) {
        Random r = new Random();
        int i;
        do {
            i = r.nextInt(LIST.length);
        } while (i == except && LIST.length > 1);
        return i;
    }
}
