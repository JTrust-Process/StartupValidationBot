package com.startupvalidationbot.dealworkspace;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/deal-workspaces")
public class DealWorkspaceController {
    private final DealWorkspaceStore store;

    public DealWorkspaceController(DealWorkspaceStore store) {
        this.store = store;
    }

    @GetMapping
    public List<JsonNode> list() {
        return store.list();
    }

    @GetMapping("/{id}")
    public JsonNode get(@PathVariable long id) {
        return store.find(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Deal workspace " + id + " not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JsonNode create(@RequestBody JsonNode payload) {
        return store.create(payload);
    }

    @PutMapping("/{id}")
    public JsonNode update(@PathVariable long id, @RequestBody JsonNode payload) {
        return store.update(id, payload);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        store.delete(id);
    }
}
