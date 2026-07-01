# Shop Offer Availability Evaluation

Shop Offer Availability is parsed as configuration data, but evaluated once while loading each Shop Menu so that `ShopMenu` only receives offers that are visible for the current Shop Date. Duplicate Shop Offer item validation remains independent of availability and rejects duplicate item ids before filtering, because config validity must not depend on date-based or deterministic-random availability results.
