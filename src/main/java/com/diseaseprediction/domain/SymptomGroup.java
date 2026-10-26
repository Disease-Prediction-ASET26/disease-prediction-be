package com.diseaseprediction.domain;

import java.util.ArrayList;
import java.util.List;

public class SymptomGroup implements SymptomComponent {

    private String name;
    private final List<SymptomComponent> children = new ArrayList<>();

    public SymptomGroup() {
    }

    public SymptomGroup(String name) {
        this.name = name;
    }

    public void add(SymptomComponent component) {
        children.add(component);
    }

    public void remove(SymptomComponent component) {
        children.remove(component);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Symptom> getSymptoms() {
        List<Symptom> all = new ArrayList<>();
        for (SymptomComponent child : children) {
            all.addAll(child.getSymptoms());
        }
        return all;
    }
}
