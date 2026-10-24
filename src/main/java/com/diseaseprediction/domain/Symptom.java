package com.diseaseprediction.domain;

import java.util.List;
import java.util.UUID;


public class Symptom {

    private UUID symptomId;
    private String name;
    private String bodyArea;
    private List<String> synonyms;
}
