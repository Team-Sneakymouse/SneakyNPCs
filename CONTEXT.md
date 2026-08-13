# SneakyNPCs

SneakyNPCs defines configurable in-game NPC interactions for quests, shops, reputation, taxes, tailoring, and external menu actions.

## Language

**NPC**:
A configured in-game persona that players interact with through menus, shops, quests, taxes, tailoring, or external actions. An NPC is not the live Minecraft entity or mob instance.
_Avoid_: Mob, entity, villager, character

**Menu**:
A player-facing interaction screen or action node attached to an NPC. Menus include selection, quest, shop, custom, external, tailor, and taxes interactions.
_Avoid_: GUI, inventory screen

**Root Menu**:
The entry menu for an NPC interaction, from which any nested menus are reached.
_Avoid_: Main GUI, home screen

**Selection Menu**:
A menu whose purpose is to present choices that lead to other menus. Other menu types may also lead to child menus when their own domain requires it.
_Avoid_: Main menu, hub, submenu selector

**Child Menu**:
A menu reached from another menu during an NPC interaction.
_Avoid_: Submenu, nested GUI

**Shop Menu**:
A menu where players buy offered items from an NPC and may sell eligible items to that NPC.
_Avoid_: Store, market, vendor GUI

**Shop Offer**:
A configured item listing in a Shop Menu that the NPC sells to the player, including its purchasable item, price, requirements, and stock limits.
_Avoid_: Shop item, listing, product

**Sellable Item**:
An item priced in a currency that allows player-to-NPC selling.
_Avoid_: Offer, shop offer

**Sell Transaction**:
A completed or attempted transaction where the player sells a Sellable Item to an NPC.
_Avoid_: Sale, sell offer, buyback

**Purchase**:
A completed or attempted transaction where the player buys a Shop Offer from an NPC.
_Avoid_: Buy transaction, sale

**Currency**:
An abstract unit of value that may be stored as physical items, bank balance, or both.
_Avoid_: Money item, coin

**Physical Currency**:
Currency stored as item stacks in a player's inventory.
_Avoid_: Item currency, money item

**Banked Currency**:
Currency stored as a player's non-inventory balance.
_Avoid_: Bank currency, variable currency

**NPC Wallet**:
The player-specific pool of currency an NPC uses to pay for Sell Transactions.
_Avoid_: Wallet, player wallet, shop balance, NPC bank

**Native Currency**:
The primary currency of an NPC Wallet.
_Avoid_: Wallet currency

**Currency Exchange**:
Converting value between currencies according to configured exchange rates.
_Avoid_: Conversion

**Exchange Path**:
The chain of Currency Exchanges used to compare or spend one currency as another.
_Avoid_: Currency graph

**Stored Value**:
The currency and amount attached to a MagicItem that determine purchase price and sell payout.
_Avoid_: PDC price, item price

**Price**:
The currency amount required to Purchase a Shop Offer.
_Avoid_: Value

**Shop Offer Availability**:
A config-load condition set that determines whether a Shop Offer is shown in a Shop Menu at all, independent of player-specific requirements and Stock. A Shop Offer is available when any of its availability conditions are true.
_Avoid_: Requirement, stock, visibility rule

**Disabled Shop Offer**:
A configured Shop Offer intentionally hidden from a Shop Menu while retained for future use, placeholders, or reference.
_Avoid_: Removed offer, unavailable stock, failed requirement

**Shop Date**:
The calendar date used to evaluate Shop Offer Availability, based on a fixed UTC-7 offset.
_Avoid_: Server date, local date, reset time

**Random Availability Group**:
A set of Shop Offers where exactly one member is chosen uniformly at config load, seeded with the Shop Date, to satisfy the random-group availability condition. Other Shop Offer Availability conditions may still make other members available.
_Avoid_: Weighted group, eligibility pool, random requirement

**Payout**:
Currency given to the player after a Sell Transaction.
_Avoid_: Refund, reward, price

**Stock**:
The available quantity of a limited Shop Offer for a player at an NPC.
_Avoid_: Limit

**Restock**:
Replenishing Stock or an NPC Wallet over time.
_Avoid_: Refill, regenerate

**Shop Item Requirement**:
A condition a player must satisfy before Purchasing a Shop Offer. A Shop Offer's Shop Item Requirements are conjunctive: the player must satisfy all of them.
_Avoid_: Requirement, shop requirement

**Reputation Requirement**:
A Shop Item Requirement satisfied when the player's Reputation with the NPC that owns the Shop Offer is at least its inclusive, potentially fractional threshold. It cannot reference another NPC's Reputation.
_Avoid_: Cross-NPC requirement, global reputation requirement

**Requirement-Hidden Shop Offer**:
A Shop Offer that is not shown to a player because the player does not satisfy a Shop Item Requirement that hides the offer. Shop Offers remain shown when the player only fails Shop Item Requirements that do not hide the offer.
_Avoid_: Unavailable offer, disabled offer, invisible item

**Quest Item**:
An item required to complete a Quest.
_Avoid_: Requirement, turn-in item

**Quest**:
A single completable step in an NPC's ordered quest sequence.
_Avoid_: Questline

**Quest Chain**:
An NPC's ordered sequence of Quests.
_Avoid_: Questline

**Active Quest**:
The first incomplete Quest in a Quest Chain.
_Avoid_: Current quest

**Quest Reward**:
An item, variable change, reputation change, or command applied when a Quest is completed.
_Avoid_: Prize, completion output

**Reputation**:
A player's standing with one NPC. A player with no recorded standing for an NPC has zero Reputation with that NPC.
_Avoid_: Friendship, global reputation

**Custom Menu**:
A menu that opens a configured custom GUI by id.
_Avoid_: External menu

**External Menu**:
A menu action that hands the player off to a MagicSpell-powered interaction.
_Avoid_: Custom menu, spell menu

**Tailor Menu**:
A menu where a player pays to edit clothing through the tailor service.
_Avoid_: Mannequin menu, clothing GUI

**Taxes Menu**:
A menu where a player pays currency to reduce outstanding taxes and receive the configured tax-payment item.
_Avoid_: Tax shop, writ shop

**Outstanding Taxes**:
A player's unpaid tax amount.
_Avoid_: Bank tax, tax balance

**Tax Reward**:
The item granted when a player pays down Outstanding Taxes.
_Avoid_: Tax writ, quest reward

**Tax Reward Value**:
The amount of Outstanding Taxes reduced by one Tax Reward.
_Avoid_: Tax value, price, stored value

**Bank Display**:
The Shop Menu display of selected Banked Currency balances.
_Avoid_: Balance tooltip, currency tooltip

**Change**:
Leftover value returned to the player after paying a Price through Currency Exchange.
_Avoid_: Refund
