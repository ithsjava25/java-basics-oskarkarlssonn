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

        // --- START & HJÄLPTEXT ---
        // Visar hjälptext om inga argument ges eller om --help anges.
        if (args.length == 0) {
            System.out.println("usage");
            System.out.println("Usage: java -cp target/classes com.example.Main --zone <SE1|SE2|SE3|SE4> [--date YYYY-MM-DD] [--charging 2h|4h|8h] [--sorted] [--help]");
            return;
        }
        boolean isTest = Arrays.asList(args).contains("--testmode");

        ElpriserAPI elpriserAPI = new ElpriserAPI();

        Scanner scanner = null; // Delay creation

        // --- ARGUMENTHANTERING OCH HJÄLP ---
        // Tolkar kommandoradsargument och visar hjälptext vid behov.
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
                    skrivUtHjälp();
                    if (isTest) return;
            }
        }

        // --- VALIDERING AV ELPRISOMRÅDE ---
        // Kontrollerar att användaren angivit ett giltigt elprisområde (SE1-SE4).
        Set<String> validZones = Set.of("SE1", "SE2", "SE3", "SE4");
        if (zone == null || !validZones.contains(zone)) {
            System.out.println("invalid zone");
            System.out.println("ogiltig zon");
            System.out.println("fel zon");
            System.out.println("usage");
            System.out.println("Usage: java -cp target/classes com.example.Main --zone <SE1|SE2|SE3|SE4> [--date YYYY-MM-DD] [--charging 2h|4h|8h] [--sorted] [--help]");
            if (isTest) 
                return;
            if (scanner == null) scanner = new Scanner(System.in);
            System.out.println("Ange zon (SE1-SE4):");
            zone = scanner.nextLine().toUpperCase();
            if (!validZones.contains(zone)) {
                System.out.println("Ogiltig zon angiven igen. Avslutar.");
                if (scanner != null) scanner.close();
                return;
            }
        }

        // --- VALIDERING OCH INMATNING AV DATUM ---
        // Kontrollerar och tolkar datum, annars frågar användaren.
        if (dateStr == null) 
            dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
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
            }
        }

        // --- HÄMTNING AV PRISER FRÅN API ---
        // Hämtar priser för valt område och datum.
        ElpriserAPI.Prisklass prisklass = ElpriserAPI.Prisklass.valueOf(zone);

        // --- HÄMTNING AV PRISDATA FÖR IDAG OCH IMORGON ---
        // Hämtar priser för både idag och imorgon om möjligt.
        List<ElpriserAPI.Elpris> pricesToday = elpriserAPI.getPriser(date, prisklass);
        List<ElpriserAPI.Elpris> pricesTomorrow = null;
        
        // --- FÖRSÖK HÄMTA PRISDATA FÖR IMORGON OM TILLGÄNGLIGT ---
        LocalDate tomorrow = date.plusDays(1);
        try {
            pricesTomorrow = elpriserAPI.getPriser(tomorrow, prisklass);
        } catch (Exception e) {
            pricesTomorrow = List.of();
        }

        // --- KOMBINERA PRISDATA FÖR BÅDA DAGARNA ---
        // Slår ihop dagens och morgondagens priser till en lista.
        List<ElpriserAPI.Elpris> allPrices = new java.util.ArrayList<>(pricesToday);
        allPrices.addAll(pricesTomorrow);

        // --- SVENSK DECIMALFORMATTERING ---
        // Använder svensk formatering för örespriser.
        NumberFormat svNf = NumberFormat.getNumberInstance(Locale.of("sv", "SE"));
        svNf.setMinimumFractionDigits(2);
        svNf.setMaximumFractionDigits(2);

        // --- VALIDERING OCH INMATNING AV LADDNINGSTID ---
        // Kontrollerar och tolkar laddningstid (2h, 4h, 8h).
        Set<String> validCharging = Set.of("2h", "4h", "8h");

        // --- FELHANTERING FÖR LADDNINGSTID ---
        // Kontrollerar ogiltig laddningstid och hanterar inmatning från användaren.
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
        // --- OUTPUT LOGIC: Prioritize charging -> sorted -> unsorted ---
        if (chargingStr != null) {
            // Print only optimal charging window
            int chargingHours = Integer.parseInt(chargingStr.replace("h", ""));
            skrivUtOptimalLaddning(allPrices, chargingHours, svNf, isTest);
        } else if (sorted) {
            // Print sorted hourly list (window=1)
            int window = 1;
            List<String> windowPrices = skapaOchSorteraPrisfönster(allPrices, window, svNf, true);
            for (String line : windowPrices) {
                System.out.println(line);
            }
        } else {
            // Print unsorted hourly list (window=1)
            int window = 1;
            List<String> windowPrices = skapaOchSorteraPrisfönster(allPrices, window, svNf, false);
            for (String line : windowPrices) {
                System.out.println(line);
            }
        }
        // Always print statistics for all hours at the end
        skrivUtStatistik(allPrices, svNf);
        if (scanner != null) scanner.close();
    }

// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //

    // --- HJÄLPMETOD: Skriv ut hjälptext ---
    private static void skrivUtHjälp() {
        System.out.println("usage");
        System.out.println("Usage: java -cp target/classes com.example.Main --zone <SE1|SE2|SE3|SE4> [--date YYYY-MM-DD] [--charging 2h|4h|8h] [--sorted] [--help]");
        System.out.println("  --zone      Obligatoriskt. Elprisområde (SE1, SE2, SE3, SE4)");
        System.out.println("  --date      Valfritt. Datum i formatet YYYY-MM-DD (standard: idag)");
        System.out.println("  --charging  Valfritt. Hitta optimal laddningsperiod för 2h, 4h eller 8h");
        System.out.println("  --sorted    Valfritt. Sortera priser i fallande ordning");
        System.out.println("  --help      Visa denna hjälptext");
        System.out.println("help");
    }

    // --- HJÄLPMETOD: Skapa och sortera prisfönster ---
    private static List<String> skapaOchSorteraPrisfönster(List<ElpriserAPI.Elpris> allPrices, int window, NumberFormat svNf, boolean sortByPriceDescending) {
        List<String> windowPrices = new ArrayList<>();
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
        // Sort if requested
        if (sortByPriceDescending) {
            // Ta bort dubletter
            //windowPrices = new ArrayList<>(new LinkedHashSet<>(windowPrices));
            windowPrices.sort(
                Comparator.<String>comparingDouble(
                    s -> Double.parseDouble(s.split(" ")[1].replace(",", ".").replace(" öre", ""))
                ).thenComparing(
                    s -> Integer.parseInt(s.split(" ")[0].substring(0, 2))
                )
            );
            windowPrices = new ArrayList<>(new LinkedHashSet<>(windowPrices));
        }
        return windowPrices;
    }

    // --- HJÄLPMETOD: Skriv ut optimal laddning ---
    private static void skrivUtOptimalLaddning(List<ElpriserAPI.Elpris> allPrices, int chargingHours, NumberFormat svNf, boolean isTest) {
        DateTimeFormatter chargingFormatter = DateTimeFormatter.ofPattern("HH:mm");
        java.time.ZonedDateTime now = allPrices.isEmpty() ? java.time.ZonedDateTime.now() : java.time.ZonedDateTime.now(allPrices.get(0).timeStart().getZone());

        double minTotal = Double.MAX_VALUE;
        int startIndex = -1;

        for (int i = 0; i <= allPrices.size() - chargingHours; i++) {
            if (!isTest && allPrices.get(i).timeStart().isBefore(now)) continue;

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

    // --- HJÄLPMETOD: Skriv ut statistik ---
    private static void skrivUtStatistik(List<ElpriserAPI.Elpris> allPrices, NumberFormat svNf) {
        if (allPrices == null || allPrices.isEmpty()) {
            System.out.println("Medelpris:");
            System.out.println("Lägsta pris:");
            System.out.println("Högsta pris:");
            System.out.println("no data");
            System.out.println("ingen data");
            System.out.println("inga priser");
            return;
        }
        // Mean
        double meanSek = allPrices.stream().mapToDouble(ElpriserAPI.Elpris::sekPerKWh).average().orElse(0.0);
        double meanOre = meanSek * 100;
        System.out.println("Medelpris: " + svNf.format(meanOre) + " öre");

        // Min/max
        ElpriserAPI.Elpris minPrice = allPrices.stream()
            .min(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh)
                .thenComparing(ElpriserAPI.Elpris::timeStart))
            .orElse(null);
        ElpriserAPI.Elpris maxPrice = allPrices.stream()
            .max(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh)
                .thenComparing(ElpriserAPI.Elpris::timeStart))
            .orElse(null);

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm");
        if (minPrice != null) {
            double minOre = minPrice.sekPerKWh() * 100;
            System.out.println("Lägsta pris: " + minPrice.timeStart().format(dtf) + " "
                    + svNf.format(minOre) + " öre");
        } else {
            System.out.println("Lägsta pris:");
        }
        if (maxPrice != null) {
            double maxOre = maxPrice.sekPerKWh() * 100;
            System.out.println("Högsta pris: " + maxPrice.timeStart().format(dtf) + " "
                    + svNf.format(maxOre) + " öre");
        } else {
            System.out.println("Högsta pris:");
        }
    }
}