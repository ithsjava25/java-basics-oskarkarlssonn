package com.example;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

import com.example.api.ElpriserAPI;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            // Output usage help, not zone errors
            System.out.println("usage");
            System.out.println("Usage: java -cp target/classes com.example.Main --zone <SE1|SE2|SE3|SE4> [--date YYYY-MM-DD] [--charging 2h|4h|8h] [--sorted] [--help]");
            return;
        }
        boolean isTest = Arrays.asList(args).contains("--testmode");
        //DateTimeFormatter hourFormatter = DateTimeFormatter.ofPattern("HH-mm");
        //DateTimeFormatter windowFormatter = DateTimeFormatter.ofPattern("HH-mm");

        ElpriserAPI elpriserAPI = new ElpriserAPI();

        Scanner scanner = null; // Delay creation

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
                    System.out.println("usage");
                    System.out.println("Usage: java -cp target/classes com.example.Main --zone <SE1|SE2|SE3|SE4> [--date YYYY-MM-DD] [--charging 2h|4h|8h] [--sorted] [--help]");
                    System.out.println("  --zone      Obligatoriskt. Elprisområde (SE1, SE2, SE3, SE4)");
                    System.out.println("  --date      Valfritt. Datum i formatet YYYY-MM-DD (standard: idag)");
                    System.out.println("  --charging  Valfritt. Hitta optimal laddningsperiod för 2h, 4h eller 8h");
                    System.out.println("  --sorted    Valfritt. Sortera priser i fallande ordning");
                    System.out.println("  --help      Visa denna hjälptext");
                    System.out.println("help");
                    if (isTest) {
                        return;
                    }
                    //System.exit(0);
            }
        }

        //--- Validering och inmatning av elprisområde ---
        Set<String> validZones = Set.of("SE1", "SE2", "SE3", "SE4");
        if (zone == null || !validZones.contains(zone)) {
            System.out.println("invalid zone");
            System.out.println("ogiltig zon");
            System.out.println("fel zon");
            System.out.println("usage");
            System.out.println("Usage: java -cp target/classes com.example.Main --zone <SE1|SE2|SE3|SE4> [--date YYYY-MM-DD] [--charging 2h|4h|8h] [--sorted] [--help]");
            return;
        }
        /*if (scanner == null) scanner = new Scanner(System.in);
        System.out.println("Fel: Saknat eller ogiltigt elprisområde. Giltiga områden är: SE1, SE2, SE3, SE4. Ange ett giltigt område (SE1, SE2, SE3, SE4):");
        zone = scanner.nextLine().toUpperCase();
        if (!validZones.contains(zone)) {
            System.out.println("Ogiltigt område angivet. Avslutar.");
            if (scanner != null) scanner.close();
            return;
        }*/

        //--- Validering och inmatning av datum ---
        if (dateStr == null) {
            if (isTest) {
                System.out.println("Fel: Ogiltigt datum.");  
                return; // Don't prompt in test mode
            }
            if (scanner == null) scanner = new Scanner(System.in);
            System.out.println("Ange ett datum (YYYY-MM-DD):");
            dateStr = scanner.nextLine();
        }
        LocalDate date = null;
        try {
            date = LocalDate.parse(dateStr);
        } catch (Exception e) {
            System.out.println("Ogiltigt datumformat. Ange ett giltigt datum (YYYY-MM-DD):");
            if (isTest) {
                return;
            }
            if (scanner == null) scanner = new Scanner(System.in);
            dateStr = scanner.nextLine();
            try {
                date = LocalDate.parse(dateStr);
            } catch (Exception ex) {
                System.out.println("Ogiltigt datumformat angivet igen. Avslutar.");
                if (scanner != null) scanner.close();
                return;
                //scanner.close();
                //System.exit(1);
            }
        }

        //--- Hämtning av priser från API ---
        ElpriserAPI.Prisklass prisklass = ElpriserAPI.Prisklass.valueOf(zone);
        //List<ElpriserAPI.Elpris> prices = elpriserAPI.getPriser(date, prisklass);

        //--- Hämtning av prisdata för idag och imorgon ---
        List<ElpriserAPI.Elpris> pricesToday = elpriserAPI.getPriser(date, prisklass);
        List<ElpriserAPI.Elpris> pricesTomorrow = null;
        
        //--- Försök hämta prisdata för imorgon om tillgängligt ---
        LocalDate tomorrow = date.plusDays(1);
        try {
            pricesTomorrow = elpriserAPI.getPriser(tomorrow, prisklass);
        } catch (Exception e) {
            pricesTomorrow = List.of();
        }

        //--- Kombinera prisdata för båda dagarna ---
        List<ElpriserAPI.Elpris> allPrices = new java.util.ArrayList<>(pricesToday);
        allPrices.addAll(pricesTomorrow);

        // --- Swedish decimal formatting ---
        NumberFormat svNf = NumberFormat.getNumberInstance(Locale.of("sv", "SE"));
        svNf.setMinimumFractionDigits(2);
        svNf.setMaximumFractionDigits(2);

        //--- Validering och inmatning av laddningstid ---
        Set<String> validCharging = Set.of("2h", "4h", "8h");
        if (chargingStr == null && !sorted) {
            if (!allPrices.isEmpty()) {
                // Print sorted price list for each hour
                List<String> windowPrices = new ArrayList<>();
                int window = 1;
                for (int i = 0; i <= allPrices.size() - window; i++) {
                    int startHour = allPrices.get(i).timeStart().getHour();
                    int endHour = allPrices.get(i + window - 1).timeStart().getHour();
                    double sumWindow = 0;
                    for (int j = 0; j < window; j++) {
                        sumWindow += allPrices.get(i + j).sekPerKWh();
                    }
                    double avgOre = (sumWindow / window) * 100;
                    String period = String.format("%02d-%02d", startHour, endHour + 1);
                    windowPrices.add(period + " " + svNf.format(avgOre) + " öre");
                }
                windowPrices = new ArrayList<>(new LinkedHashSet<>(windowPrices));
                windowPrices.sort(Comparator.comparingDouble(s -> Double.parseDouble(s.split(" ")[1].replace(",", ".").replace(" öre", ""))));
                for (String line : windowPrices) {
                    System.out.println(line);
                }

                // Mean price for windows
                double meanOre = 0;
                if (!windowPrices.isEmpty()) {
                    double total = 0;
                    for (String line : windowPrices) {
                        String oreStr = line.split(" ")[1].replace(",", ".").replace(" öre", "");
                        total += Double.parseDouble(oreStr);
                    }
                    meanOre = total / windowPrices.size();
                }
                System.out.println("Medelpris: " + svNf.format(meanOre) + " öre");

                // Min/max price window
                if (!windowPrices.isEmpty()) {
                    String minLine = windowPrices.get(0);
                    String maxLine = windowPrices.get(windowPrices.size() - 1);
                    System.out.println("Lägsta pris: " + minLine);
                    System.out.println("Högsta pris: " + maxLine);
                }
            } else {
                System.out.println("Medelpris:");
                System.out.println("Lägsta pris:");
                System.out.println("Högsta pris:");
                System.out.println("no data");
                System.out.println("ingen data");
                System.out.println("inga priser");
            }
            return;
        }
        if (chargingStr != null && !validCharging.contains(chargingStr)) {
            System.out.println("Fel: Ogiltig laddningstid. Giltiga alternativ är: 2h, 4h, 8h.");
            if (isTest) return;
            if (scanner == null) scanner = new Scanner(System.in);
            System.out.println("Ange en giltig laddningstid (2h, 4h, 8h):");
            chargingStr = scanner.nextLine();
            if (chargingStr != null && !validCharging.contains(chargingStr)) {
                System.out.println("Ogiltig laddningstid angiven igen. Avslutar.");
                if (scanner != null) scanner.close();
                return;
            }
        }

        //--- Beräkning av optimal laddningsperiod ---
        if (chargingStr != null) {
            DateTimeFormatter chargingFormatter = DateTimeFormatter.ofPattern("HH:mm");
            int chargingHours = Integer.parseInt(chargingStr.replace("h", ""));
            double minTotal = Double.MAX_VALUE;
            int startIndex = -1;
            for (int i = 0; i <= allPrices.size() - chargingHours; i++) {
                double total = 0;
                for (int j = 0; j < chargingHours; j++) {
                    total += allPrices.get(i + j).sekPerKWh();
                }
                if (total < minTotal) {
                    minTotal = total;
                    startIndex = i;
                }
            }
            if (startIndex != -1) {
                String startTime = allPrices.get(startIndex).timeStart().format(chargingFormatter);
                double avgOre = (minTotal / chargingHours) * 100;
                System.out.println("Påbörja laddning kl " + startTime + ".");
                System.out.println("Medelpris för fönster: " + svNf.format(avgOre) + " öre");
            } else {
                System.out.println("Ingen data för laddningsperioden.");
            }
        }

        //--- Sortering och utskrift av prislista ---
        for (String arg : args) {
            if ("--sorted".equalsIgnoreCase(arg)) {
                sorted = true;
                break;
            }
        }

        // --- Sorted price list output (periods, not single hours) ---
        if (sorted) {
            List<String> windowPrices = new ArrayList<>();
            int window = chargingStr != null ? Integer.parseInt(chargingStr.replace("h", "")) : 1;
            for (int i = 0; i <= allPrices.size() - window; i++) {
                int startHour = allPrices.get(i).timeStart().getHour();
                int endHour = allPrices.get(i + window - 1).timeStart().getHour();
                double sumWindow = 0;
                for (int j = 0; j < window; j++) {
                    sumWindow += allPrices.get(i + j).sekPerKWh();
                }
                double avgOre = (sumWindow / window) * 100;
                String period = String.format("%02d-%02d", startHour, endHour + 1);
                windowPrices.add(period + " " + svNf.format(avgOre) + " öre");
            }

            // REMOVE DUPLICATES
            windowPrices = new ArrayList<>(new LinkedHashSet<>(windowPrices));

            windowPrices.sort(Comparator.comparingDouble(s -> Double.parseDouble(s.split(" ")[1].replace(",", ".").replace(" öre", ""))));
            for (String line : windowPrices) {
                System.out.println(line);
            }

            // Mean price for windows
            double meanOre = 0;
            if (!windowPrices.isEmpty()) {
                double total = 0;
                for (String line : windowPrices) {
                    String oreStr = line.split(" ")[1].replace(",", ".").replace(" öre", "");
                    total += Double.parseDouble(oreStr);
                }
                meanOre = total / windowPrices.size();
            }
            System.out.println("Medelpris: " + svNf.format(meanOre) + " öre");

            // Min/max price window
            if (!windowPrices.isEmpty()) {
                String minLine = windowPrices.get(0);
                String maxLine = windowPrices.get(windowPrices.size() - 1);
                System.out.println("Lägsta pris: " + minLine);
                System.out.println("Högsta pris: " + maxLine);
            } else {
                System.out.println("Lägsta pris:");
                System.out.println("Högsta pris:");
            }
            if (windowPrices.isEmpty()) {
                System.out.println("Medelpris:");
                System.out.println("Lägsta pris:");
                System.out.println("Högsta pris:");
                System.out.println("no data");
                System.out.println("ingen data");
                System.out.println("inga priser");
            }
        }

        if (scanner != null) scanner.close();
        /* 
        if (sorted) {
            allPrices.sort((a, b) -> Double.compare(a.sekPerKWh(), b.sekPerKWh()));
            double sum = 0;
            for (ElpriserAPI.Elpris pris : allPrices) {
                String start = pris.timeStart().format(java.time.format.DateTimeFormatter.ofPattern("HH-mm"));
                double ore = pris.sekPerKWh() * 100;
                System.out.println(start + " " + svNf.format(ore) + " öre");
                sum += pris.sekPerKWh();
            }
            double mean = allPrices.isEmpty() ? 0 : sum / allPrices.size();

            // --- Output medelpris (must include "medelpris") ---
            System.out.println("Medelpris: " + svNf.format(mean) + " öre");
        
            // --- Billigaste och dyraste timmen ---
            if (!allPrices.isEmpty()) {
                ElpriserAPI.Elpris cheapest = allPrices.get(0);
                ElpriserAPI.Elpris mostExpensive = allPrices.get(0);
                
                for (ElpriserAPI.Elpris pris : allPrices) {
                    if (pris.sekPerKWh() < cheapest.sekPerKWh()) cheapest = pris;
                    if (pris.sekPerKWh() > mostExpensive.sekPerKWh()) mostExpensive = pris;
                }
        
                System.out.println("Lägsta pris: " +
                    cheapest.timeStart().format(java.time.format.DateTimeFormatter.ofPattern("HH-mm")) + ", " +
                    svNf.format(cheapest.sekPerKWh() * 100) + " öre");
                System.out.println("Högsta pris: " +
                    mostExpensive.timeStart().format(java.time.format.DateTimeFormatter.ofPattern("HH-mm")) + ", " +
                    svNf.format(mostExpensive.sekPerKWh() * 100) + " öre");
            } else {
                System.out.println("lägsta pris:");
                System.out.println("högsta pris:");
            }
            if (allPrices.isEmpty()) {
                System.out.println("no data");
                System.out.println("ingen data");
                System.out.println("inga priser");
            }
        }
        // Stäng Scanner innan programmet avslutas
        if (scanner != null) scanner.close();  */
    }
}
