package com.normbuild.regulation.service;

import java.util.List;

public record OfficialWebContextDocument(String title, String url, String hostname, List<String> snippets) {
}
