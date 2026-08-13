package com.example.aiagentpublisher.bot;

public final class BotReplies {

    public static final String ASK_IDEA =
            "Опишите вашу идею — что продаём? Например: «продаю ноутбуки».";
    public static final String CATEGORY_CONFIRM =
            "Категория: %s%nВерно? Ответьте «да» или пришлите свой вариант категории.";
    public static final String ASK_EXAMPLES =
            "Теперь пришлите 3–5 текстов успешных объявлений из этой категории "
                    + "(каждое отдельным сообщением). Когда закончите — /done.";
    public static final String EXAMPLE_ACCEPTED = "Пример %d принят. Ещё один или /done.";
    public static final String EXAMPLES_LIMIT = "Максимум 5 примеров. Отправьте /done.";
    public static final String NEED_EXAMPLES =
            "Нужен хотя бы один пример объявления. Пришлите текст или /cancel.";
    public static final String FEW_EXAMPLES_WARNING =
            "Примеров меньше трёх — анализ будет менее точным, но продолжаю.";
    public static final String LLM_ERROR =
            "Не получилось получить ответ от ИИ. Попробуйте ещё раз чуть позже — ваши данные сохранены.";
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
