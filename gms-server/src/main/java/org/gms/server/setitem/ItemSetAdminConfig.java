package org.gms.server.setitem;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Web-console representation of the server's item-set definitions. */
@Data
public class ItemSetAdminConfig {
    private boolean enabled = true;
    private List<Definition> sets = new ArrayList<>();

    @Data
    public static class Definition {
        private int setId;
        private String name = "";
        private List<Integer> itemIds = new ArrayList<>();
        private List<Tier> tiers = new ArrayList<>();
    }

    @Data
    public static class Tier {
        private int pieceCount;
        private Map<String, Integer> stats = new LinkedHashMap<>();
    }
}
