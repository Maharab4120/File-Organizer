package com.fileorganizer;

import com.fileorganizer.core.RuleEngine;
import com.fileorganizer.core.RuleFetcher;

public class RuleSmokeTest {
    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0]
                : "https://gist.githubusercontent.com/Maharab4120/16be6c588ff61802caf3f0e8bbbfb86c/raw/cf03accc9ca19721d73febf1bcd3965d01442735/cloudsort-rules.json";

        RuleEngine engine = RuleFetcher.fetch(url);

        System.out.println("Name:         " + engine.getName());
        System.out.println("Version:      " + engine.getVersion());
        System.out.println("Fallback:     " + engine.getFallbackFolder());
        System.out.println("Categories:   " + engine.getCategories().size());
        System.out.println("Compound:     " + engine.getSpecialRules().getCompoundExtensions().size());
        System.out.println("CaseSensitive:" + engine.getSettings().isCaseSensitive());
        System.out.println();

        String[] samples = {
                "photo.jpg", "report.pdf", "song.mp3",
                "backup.tar.gz", "backup.tar.zst",
                "font.ttf", "data.sqlite", "model.glb",
                "unknown.xyz", "noext"
        };
        for (String s : samples) {
            System.out.printf("%-20s -> %s%n", s, engine.findCategory(s));
        }
    }
}