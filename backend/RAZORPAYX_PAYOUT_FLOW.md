# RazorpayX Payout Flow (Phase 2 reference)

Internal doc for implementing vendor payouts via RazorpayX. Do not call Payout API until RazorpayX account is verified and Add Balance is done.

## Auth

- RazorpayX uses **separate API keys** from Payments (dashboard.razorpay.com).
- Get keys from **x.razorpay.com** (Banking+) → Settings → API Keys.
- Store in config/env: `RAZORPAYX_KEY_ID`, `RAZORPAYX_KEY_SECRET`. Do not use Payments key_id/key_secret for Payout API.

## 1. Contacts

- **Purpose:** Beneficiary (vendor) for payouts.
- **API:** Create Contact with name, email/phone.
- **Returns:** `contact_id`.
- **When:** On first payout for a vendor, or pre-create when vendor adds bank details. Optional: store `contact_id` per vendor (e.g. in User or VendorPayoutAccount table) to avoid creating again.

**Docs:** https://razorpay.com/docs/x/contacts/

## 2. Fund account

- **Purpose:** Bank account or UPI where payout is sent.
- **API:** Create Fund account linked to contact:
  - **Bank:** account_type = `bank_account`, account_number, ifsc, account_holder_name.
  - **UPI:** account_type = `vpa`, vpa = UPI ID.
- **Returns:** `fund_account_id`.
- **When:** After Contact; use vendor’s stored bank/UPI (User.accountHolderName, accountNumber, ifscCode, upiId). Optional: store `fund_account_id` per vendor so Phase 2 does not create every time.

**Docs:** https://razorpay.com/docs/x/fund-accounts/

## 3. Payout

- **Purpose:** Send 85% (settlement amount) to vendor.
- **API:** Create Payout:
  - amount in **paise**
  - fund_account_id (vendor’s)
  - purpose/narration (e.g. "Vendor payout – Order XYZ")
  - reference (e.g. booking_id or order_id for idempotency/audit)
- **Idempotency:** Use booking_id (or order_id) so same booking is not paid twice.
- **On success:** Set Booking.paidToVendor = true, payoutId, payoutDate.

**Docs:** https://razorpay.com/docs/x/payouts/ and https://razorpay.com/docs/api/x/payouts/

## 4. Webhook (optional)

- Payout status (processed/failed/reversed) can be received via webhook.
- Use for updating Booking status or retry logic in Phase 2.

## 5. 3-piece payout (25% + 25% + 50%)

Vendor receives settlementAmount in three instalments:

- **Phase 1 (25%):** When site is live and execution proof uploaded. Set Booking.paid25OnLive = true after payout.
- **Phase 2 (25%):** When mid of booking period (startDate + half of duration). Set Booking.paid25OnMid = true after payout.
- **Phase 3 (50%):** When booking duration complete (endDate passed). Set Booking.paid50OnEnd = true and Booking.paidToVendor = true after payout.

GET /api/vendors/bookings/eligible-payout returns list of EligiblePayoutResponse (bookingId, orderId, phase 1|2|3, amount). Each item is one instalment due. When calling Payout API, use the amount and phase for that item; after success update the corresponding paid25OnLive / paid25OnMid / paid50OnEnd.

## 6. Flow summary

1. Ensure RazorpayX balance (Add Balance on x.razorpay.com).
2. For a booking eligible for payout: get vendor (Booking.vendor), get vendor’s bank/UPI from User.
3. Create or get Contact → Create or get Fund account (using vendor bank/UPI).
4. Create Payout: amount = item.amount * 100 (paise), fund_account_id, purpose, reference = booking_id + phase (e.g. bookingId-phase1).
5. On success: update Booking (paid25OnLive or paid25OnMid or paid50OnEnd; if phase 3 set paidToVendor = true, payoutId, payoutDate).
