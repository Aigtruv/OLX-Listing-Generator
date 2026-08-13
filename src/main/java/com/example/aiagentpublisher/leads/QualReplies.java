package com.example.aiagentpublisher.leads;

import java.util.List;

public final class QualReplies {

    public static final String NOTHING_FOR_SALE = "Сейчас ничего не продаётся.";
    public static final String INVALID_CHOICE = "Отправьте номер из списка.";
    public static final String ASK_CITY = "В каком вы городе?";
    public static final String ASK_BUDGET = "Какой у вас бюджет?";
    public static final String ASK_TIMEFRAME = "Когда готовы купить?";
    public static final String THANKS = "Спасибо, продавец свяжется с вами.";
    public static final String STOPPED = "Ок, остановил.";
    public static final String LISTING_GONE = "Это объявление уже недоступно. Напишите ещё раз.";

    private QualReplies() {
    }

    public static String menu(List<String> titles) {
        StringBuilder sb = new StringBuilder("Что вас интересует? Отправьте номер:\n\n");
        for (int i = 0; i < titles.size(); i++) {
            sb.append(i + 1).append(". ").append(titles.get(i)).append("\n");
        }
        return sb.toString();
    }

    public static String sellerPing(String title, String waId, String city, String budget, String timeframe) {
        return "Новый лид по «" + title + "»\n\n"
                + "WhatsApp: " + waId + "\n"
                + "Город: " + city + "\n"
                + "Бюджет: " + budget + "\n"
                + "Когда: " + timeframe;
    }
}
