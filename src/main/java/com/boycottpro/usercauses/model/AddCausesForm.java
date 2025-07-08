package com.boycottpro.usercauses.model;

import java.util.List;

public class AddCausesForm {
    private String user_id;
    private List<Reason> causes;

    public AddCausesForm() {
    }

    public AddCausesForm(String user_id, List<Reason> causes) {
        this.user_id = user_id;
        this.causes = causes;
    }

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public List<Reason> getCauses() {
        return causes;
    }

    public void setCauses(List<Reason> causes) {
        this.causes = causes;
    }
}
