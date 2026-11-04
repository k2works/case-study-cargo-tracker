import { graphql } from '@/types/gql-command';

/** US02：荷主の登録（冪等性キー必須）。 */
export const RegisterShipperDocument = graphql(`
  mutation RegisterShipper(
    $idempotencyKey: String!
    $name: String!
    $address: String!
    $contact: String!
    $email: String!
    $acknowledgedDuplicate: Boolean
  ) {
    registerShipper(
      idempotencyKey: $idempotencyKey
      name: $name
      address: $address
      contact: $contact
      email: $email
      acknowledgedDuplicate: $acknowledgedDuplicate
    ) {
      shipperId
    }
  }
`);

/** US04：貨物予約の登録（冪等性キー必須。予約 ID はクライアントが生成する ULID。ADR-003）。 */
export const RegisterBookingDocument = graphql(`
  mutation RegisterBooking(
    $idempotencyKey: String!
    $bookingId: String!
    $shipperId: String!
    $itemName: String!
    $weightKg: String!
    $lengthCm: String!
    $widthCm: String!
    $heightCm: String!
    $quantity: Int!
    $origin: String!
    $destination: String!
    $preferredHandover: String!
    $deadline: String!
  ) {
    registerBooking(
      idempotencyKey: $idempotencyKey
      bookingId: $bookingId
      shipperId: $shipperId
      itemName: $itemName
      weightKg: $weightKg
      lengthCm: $lengthCm
      widthCm: $widthCm
      heightCm: $heightCm
      quantity: $quantity
      origin: $origin
      destination: $destination
      preferredHandover: $preferredHandover
      deadline: $deadline
    ) {
      bookingId
      number
      version
    }
  }
`);
