# Category attributes

A category used to be a name and a slug. A listing's specs were free-form key/value
text — nothing told a seller that a phone needs a screen size, and nothing stopped
`Size: gigantic` being saved against a shirt.

Now a category declares what its listings are expected to specify: a code, a type, a
unit, a group heading, and for dropdowns the values that are allowed. The storefront
draws its listing form and its spec table from that declaration, and the API checks
what a seller sends against it.

```
Category ──< CategoryAttribute ──< CategoryAttributeOption
                    ▲
                    │ definition (nullable)
Listing  ──< ListingAttribute
```

`ListingAttribute.key` is still stored on the row and is still what a facet filters
on. `definition` is only how the label, unit and group are reached, so an attribute
survives its definition being soft-deleted or re-pointed.

## Inheritance

Attributes apply down the tree. Declaring `brand` on Electronics means every phone
and laptop beneath it has a brand, without Phones declaring anything. The nearest
declaration of a code wins, so Phones can narrow the inherited `brand` to a dropdown
of the brands it stocks by declaring its own — and Electronics is left alone.

`GET .../attributes` folds ancestors in by default; `?includeInherited=false` is the
admin view of what a category declares itself. An inherited attribute is edited on
the category that owns it — `ownerCategorySlug` on the response says which.

## Types

| Type | Value it accepts | Stored as |
|---|---|---|
| `TEXT` | anything | trimmed |
| `NUMBER` | a number within `minValue`/`maxValue`; the unit is **not** part of the value | `6.70` → `6.7`, `5000` → `5000` |
| `BOOLEAN` | `true/false/yes/no/y/n/1/0` | `true` or `false` |
| `SELECT` | one option, matched on value or label, case-insensitively | the option's canonical value |
| `MULTI_SELECT` | comma-separated options | canonical values, `", "`-joined, deduped |

Normalising is not cosmetic: a facet compares stored values, so two sellers typing
`amoled` and `AMOLED` have to end up on the same shelf.

Keys are matched tolerantly — `screen_size`, `screen-size` and `Screen size` all find
the same definition, as does the attribute's label — and the code is what gets stored.

## What is enforced, and what is not

Required attributes must be present, and values must fit their type, option list and
bounds. **Keys the category does not define are still accepted**, as custom specs
(`"custom": true` on the response). Two reasons: a seller who wants to advertise the
colour of the box should be able to, and every listing written before its category had
a schema would otherwise become uneditable.

A category with no attributes declared rejects nothing, which is where every category
starts.

Validation runs on `POST /listings`, on the `/listing_attributes` routes, and on a
`PATCH /listings/{uuid}` that moves the listing to another category — the schema moves
with it. That PATCH now also accepts `listingAttributes`, which **replaces** the set
outright; it is how the new category's required specs are supplied in the same call as
the move.

## Endpoints

Read is public, writes are `ADMIN` — both inherited from the existing path rules on
`/api/v1/categories/**`.

| Method | Path | |
|---|---|---|
| GET | `/api/v1/categories/{uuid}/attributes` | schema, grouped |
| GET | `/api/v1/categories/slug/{slug}/attributes` | the same, by slug |
| POST | `/api/v1/categories/{uuid}/attributes` | declare attributes (takes a list) |
| PATCH | `/api/v1/categories/{uuid}/attributes/{attributeUuid}` | edit one |
| DELETE | `/api/v1/categories/{uuid}/attributes/{attributeUuid}` | soft-delete one |

Deleting is soft: listings keep the values they were saved with, the attribute simply
stops being offered, required or filtered on.

## On a listing

`ListingResponse` gained `specifications` — the same specs as `listingAttributes`, but
sectioned and ordered the way the category declares them, which is what a product page
prints:

```json
"specifications": [
  { "name": "Display", "attributes": [
      { "key": "screen_size", "label": "Screen size", "value": "6.7", "unit": "in",
        "group": "Display", "dataType": "NUMBER", "custom": false }
  ]},
  { "name": "Performance", "attributes": [ … ] },
  { "name": "Other",       "attributes": [ … ] }
]
```

`Other` is where ungrouped and custom specs print. `listingAttributes` is unchanged in
meaning and gained the same display fields, so existing clients keep working.

## Faceted search

Attributes marked `filterable` become facets on the catalogue:

```
GET /api/v1/listings?categorySlug=phones&attr=ram:8 GB&attr=panel:AMOLED&minPrice=200
```

Different keys narrow together, repeats of one key widen — `(ram = 8 GB OR ram = 12 GB)
AND panel = AMOLED`. Each facet is an `EXISTS` subquery rather than a join, because
joining a listing's attributes would return the listing once per matching row and break
the page count. A facet that is not `key:value` is refused rather than dropped: silently
ignoring it would answer a broader question than the one asked.

## Seeded schemas

On startup, categories whose slug the seeder recognises get the schema that kind of
product has in a real shop:

| Blueprint | Slugs it looks for |
|---|---|
| Phones | `phones`, `smartphones`, `mobile-phones`, `mobiles`, `phone` |
| Laptops | `laptops`, `notebooks`, `laptop` |
| Shirts | `t-shirts`, `tshirts`, `t-shirt`, `shirts`, `shirt`, `tops` |
| Shoes | `shoes`, `footwear`, `sneakers`, `shoe` |
| Watches | `watches`, `watch`, `wristwatches` |
| Headphones | `headphones`, `earphones`, `earbuds`, `audio` |

It **creates no categories** — a shop that does not sell watches should not acquire a
watches schema — and skips any category that already has attributes of its own, deleted
ones included. An admin who removed the seeded `gsm` meant it, and a restart must not
put it back. So it is a no-op on the second run and after any edit.

Disable with `app.categories.seed-attributes=false` (`CATEGORY_SEED_ATTRIBUTES`).

To add a blueprint, add a method to `CategoryAttributeSeeder` and list it in
`blueprints()`; position in the list becomes the sort order.

## Schema migration

`ddl-auto: update` creates `category_attributes` and `category_attribute_options`, and
adds a nullable `category_attribute_uuid` to `listing_attributes`. Existing rows are
untouched: their `definition` stays null and they read as custom specs until the
listing is next saved with a category that defines their key.
