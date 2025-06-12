package com.app.kira.model.task;



import org.jsoup.nodes.Element;

import java.util.List;
import java.util.function.Function;

public record OddsConfig<T>(String keyword, Function<List<Element>, T> rowMapper) {
}

