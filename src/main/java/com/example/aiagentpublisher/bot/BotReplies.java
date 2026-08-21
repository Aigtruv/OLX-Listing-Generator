package com.example.aiagentpublisher.bot;

public final class BotReplies {

    public static final String ASK_IDEA =
            "Опишите вашу идею — что продаём? Например: «продаю ноутбуки».";
    public static final String SOURCING_SEARCHING =
            "Ищу закупки на AliExpress, Alibaba, 1688, Amazon.de, eBay.de…";
    public static final String SOURCING_NONE =
            "Не нашёл предложений с ценой за 1 шт. Напишите «пропустить» или /cancel.";
    public static final String SOURCING_PICK =
            "Ответьте 1, 2 или 3 — или «пропустить».";
    public static final String SOURCING_BAD_PICK =
            "Ответьте 1, 2, 3 или «пропустить».";
    public static final String CATEGORY_CONFIRM =
            "Категория: %s%nВерно? Ответьте «да» или пришлите свой вариант категории.";
    public static final String ASK_EXAMPLES =
            "Теперь пришлите 3–5 ссылок на объявления olx.kz (каждое отдельным сообщением), например:\n"
                    + "https://www.olx.kz/d/obyavlenie/noutbuk-dell-inspiron-3162-IDqzxSX.html\n"
                    + "Когда закончите — /done.";
    public static final String ASK_OLX_URL =
            "Пришлите ссылку на объявление olx.kz, например:\n"
                    + "https://www.olx.kz/d/obyavlenie/…";
    public static final String OLX_FETCH_FAILED =
            "Не удалось открыть объявление. Пришлите другую ссылку или вставьте текст объявления.";
    public static final String EXAMPLE_ACCEPTED = "Пример %d принят. Ещё один или /done.";
    public static final String EXAMPLES_LIMIT = "Максимум 5 примеров. Отправьте /done.";
    public static final String NEED_EXAMPLES =
            "Нужен хотя бы один пример объявления. Пришлите ссылку olx.kz или /cancel.";
    public static final String FEW_EXAMPLES_WARNING =
            "Примеров меньше трёх — анализ будет менее точным, но продолжаю.";
    public static final String LLM_ERROR =
            "Не получилось получить ответ от ИИ. Попробуйте ещё раз чуть позже — ваши данные сохранены.";
    public static final String GENERATING =
            "Готовлю объявление, это займёт около минуты. Не отправляйте /done повторно.";
    public static final String CANCELLED = "Ок, отменил. /new — начать заново.";
    public static final String PUBLISHED_OK = "Отметил как опубликованное: «%s». Удачных продаж!";
    public static final String NOTHING_TO_PUBLISH =
            "Нет свежих сгенерированных объявлений. Сначала /new.";
    public static final String NO_CASES = "Пока нет ни одного объявления. Начните с /new.";
    public static final String HINT =
            "Команды: /new — новое объявление, /status — мои объявления, "
                    + "/published — отметить опубликованным, /cancel — отменить.";
    public static final String SIMILARITY_WARNING =
            "⚠️ Текст всё ещё похож на примеры — перед публикацией перефразируйте вручную.";

    private BotReplies() {
    }
}
