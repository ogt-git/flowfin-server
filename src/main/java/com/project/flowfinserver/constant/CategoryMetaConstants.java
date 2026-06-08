package com.project.flowfinserver.constant;

import com.project.flowfinserver.domain.CategoryType;

import java.util.Map;

public final class CategoryMetaConstants {

    private CategoryMetaConstants() {}

    // id, name, icon, color, type — icon·color은 DB에 없으므로 코드 상수로 관리
    public record CategoryMeta(int id, String name, String icon, String color, CategoryType type) {}

    public static final Map<Integer, CategoryMeta> META = Map.ofEntries(
            Map.entry(1,  new CategoryMeta(1,  "주거비",     "🏠",  "#4F46E5", CategoryType.FIXED)),
            Map.entry(2,  new CategoryMeta(2,  "보험비",     "🛡️",  "#7C3AED", CategoryType.FIXED)),
            Map.entry(3,  new CategoryMeta(3,  "통신비",     "📱",  "#9333EA", CategoryType.FIXED)),
            Map.entry(4,  new CategoryMeta(4,  "교육비",     "📚",  "#A855F7", CategoryType.FIXED)),
            Map.entry(5,  new CategoryMeta(5,  "식비",       "🍽️",  "#10B981", CategoryType.VARIABLE)),
            Map.entry(6,  new CategoryMeta(6,  "생활비",     "🛒",  "#059669", CategoryType.VARIABLE)),
            Map.entry(7,  new CategoryMeta(7,  "교통비",     "🚌",  "#34D399", CategoryType.VARIABLE)),
            Map.entry(8,  new CategoryMeta(8,  "의류비",     "👕",  "#6EE7B7", CategoryType.VARIABLE)),
            Map.entry(9,  new CategoryMeta(9,  "문화여가비",  "🎬",  "#A7F3D0", CategoryType.VARIABLE)),
            Map.entry(10, new CategoryMeta(10, "의료비",     "🏥",  "#F59E0B", CategoryType.ETC)),
            Map.entry(11, new CategoryMeta(11, "기타지출",   "💸",  "#D1D5DB", CategoryType.ETC))
    );
}
