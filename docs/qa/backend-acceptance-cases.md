# Backend acceptance cases

These are manual acceptance scenarios, not evidence that the checks have passed.

001. **Bogota D.C. / coverage:** Coverage reports indexed source and section counts when documents exist, or NO_OFFICIAL_SOURCE when none exist.
002. **Bogota D.C. / discovery:** Official-source discovery returns traceable HTTPS candidates and labels each result as pending legal review.
003. **Bogota D.C. / provenance:** A retrieved excerpt includes its exact official source URL and does not present a search snippet as a verified article.
004. **Bogota D.C. / jurisdiction:** The compliance review uses only sources assigned to this jurisdiction and never applies rules from another municipality.
005. **Bogota D.C. / arithmetic:** Given a 200 square meter lot, 100 square meter first-floor footprint, and 200 square meter total built area, proposed occupancy is 0.50 and construction is 1.00.
006. **Bogota D.C. / missing-area:** When first-floor footprint is absent, proposed occupancy remains unknown while other independently supplied figures remain available.
007. **Bogota D.C. / legal-limit:** The answer withholds parcel-specific height, setback, and maximum-index claims without a matching official planning sheet.
