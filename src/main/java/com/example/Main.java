package com.example;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import com.example.api.ElpriserAPI;

public class Main {
    public static void main(String[] args) {
        ElpriserAPI elpriserAPI = new ElpriserAPI();

        //--- Argumenthantering och hjälptext ---
        String zone = null, dateStr = null, chargingStr = null;
        boolean sorted = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i].toLowerCase()) {
                case "--zone":
                    if (i + 1 < args.length) zone = args[++i].toUpperCase();
                    break;
                case "--date":
                    if (i + 1 < args.length) dateStr = args[++i];
                    break;
                case "--charging":
                    if (i + 1 < args.length) chargingStr = args[++i];
                    break;
                case "--sorted":
                    sorted = true;
                    break;
                case "--help":
                    System.out.println("Användning: java -cp target/classes com.example.Main --zone <SE1|SE2|SE3|SE4> [--date YYYY-MM-DD] [--charging 2h|4h|8h] [--sorted] [--help]");
                    System.out.println("  --zone      Obligatoriskt. Elprisområde (SE1, SE2, SE3, SE4)");
                    System.out.println("  --date      Valfritt. Datum i formatet YYYY-MM-DD (standard: idag)");
                    System.out.println("  --charging  Valfritt. Hitta optimal laddningsperiod för 2h, 4h eller 8h");
                    System.out.println("  --sorted    Valfritt. Sortera priser i fallande ordning");
                    System.out.println("  --help      Visa denna hjälptext");
                    System.exit(0);
            }
        }

        //--- Validering och inmatning av elprisområde ---
        Set<String> validZones = Set.of("SE1", "SE2", "SE3", "SE4");
        if (zone == null || !validZones.contains(zone)) {
            System.out.println("Fel: Saknat eller ogiltigt elprisområde. Giltiga områden är: SE1, SE2, SE3, SE4. Ange ett giltigt område (SE1, SE2, SE3, SE4):");
            Scanner scanner = new Scanner(System.in);
            zone = scanner.nextLine().toUpperCase();
            if (!validZones.contains(zone)) {
                System.out.println("Ogiltigt område angivet. Avslutar.");
                System.exit(1);
            }
        }

        //--- Validering och inmatning av datum ---
        if (dateStr == null) {
            System.out.println("Ange ett datum (YYYY-MM-DD):");
            Scanner scanner = new Scanner(System.in);
            dateStr = scanner.nextLine();
        }
        LocalDate date = null;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            System.out.println("Ogiltigt datumformat. Ange ett giltigt datum (YYYY-MM-DD):");
            Scanner scanner = new Scanner(System.in);
            dateStr = scanner.nextLine();
            try {
                date = LocalDate.parse(dateStr);
            } catch (Exception ex) {
                System.out.println("Ogiltigt datumformat angivet igen. Avslutar.");
                System.exit(1);
            }
        }

        //--- Hämtning av priser från API ---
        ElpriserAPI.Prisklass prisklass = ElpriserAPI.Prisklass.valueOf(zone);
        List<ElpriserAPI.Elpris> prices = elpriserAPI.getPriser(date, prisklass);

        //--- Validering och inmatning av laddningstid ---
        Set<String> validCharging = Set.of("2h", "4h", "8h");
        if (chargingStr != null && !validCharging.contains(chargingStr)) {
            System.out.println("Fel: Ogiltig laddningstid. Giltiga alternativ är: 2h, 4h, 8h.");
            System.out.println("Ange en giltig laddningstid (2h, 4h, 8h):");
            Scanner scanner = new Scanner(System.in);
            chargingStr = scanner.nextLine();
            if (chargingStr != null && !validCharging.contains(chargingStr)) {
                System.out.println("Ogiltig laddningstid angiven igen. Avslutar.");
                System.exit(1);
            }
        }

        //--- Beräkning av optimal laddningsperiod ---
        if (chargingStr != null) {
            int chargingHours = Integer.parseInt(chargingStr.replace("h", ""));
            double minTotal = Double.MAX_VALUE;
            int startIndex = -1;
            for (int i = 0; i <= prices.size() - chargingHours; i++) {
                double total = 0;
                for (int j = 0; j < chargingHours; j++) {
                    total += prices.get(i + j).sekPerKWh();
                }
                if (total < minTotal) {
                    minTotal = total;
                    startIndex = i;
                }
            }
            if (startIndex != -1) {
                System.out.printf("Optimal laddningsperiod: %s till %s, total kostnad: %.2f SEK%n",
                    prices.get(startIndex).timeStart(),
                    prices.get(startIndex + chargingHours - 1).timeEnd(),
                    minTotal);
            } else {
                System.out.println("Inte tillräckligt med prisdata för önskad laddningsperiod.");
            }
        }

        //--- Sortering och utskrift av prislista ---
        for (String arg : args) {
            if ("--sorted".equalsIgnoreCase(arg)) {
                sorted = true;
                break;
            }
        }
        if (sorted) {
            prices.sort((a, b) -> Double.compare(b.sekPerKWh(), a.sekPerKWh()));
            System.out.println("Elpriser (sorterade, dyrast först):");
            for (ElpriserAPI.Elpris pris : prices) {
                System.out.printf("%s - %s: %.2f SEK/kWh%n", pris.timeStart(), pris.timeEnd(), pris.sekPerKWh());
            }
        }   
    }
}
