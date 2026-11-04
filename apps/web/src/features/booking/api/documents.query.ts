import { graphql } from '@/types/gql-query';

/** SC-20：荷主の一覧・検索（営業担当者）。 */
export const ShippersDocument = graphql(`
  query Shippers($keyword: String) {
    shippers(keyword: $keyword) {
      id
      name
      email
      kind
    }
  }
`);

/** SC-22：荷主の詳細。 */
export const ShipperDocument = graphql(`
  query Shipper($id: String!) {
    shipper(id: $id) {
      id
      kind
      name
      address
      contact
      email
    }
  }
`);

/** SC-25：予約の一覧。応答の版（version）に追いつくまで再取得するために version を含める。 */
export const BookingsDocument = graphql(`
  query Bookings($status: String, $shipperId: String) {
    bookings(status: $status, shipperId: $shipperId) {
      id
      number
      shipperId
      status
      origin
      destination
      deadline
      version
      availableCommands
    }
  }
`);

/** SC-27：予約の詳細（貨物・輸送条件・履歴）。 */
export const BookingDocument = graphql(`
  query Booking($id: String!) {
    booking(id: $id) {
      id
      number
      shipperId
      status
      version
      availableCommands
      origin
      destination
      deadline
      preferredHandover
      cargo {
        itemName
        weightKg
        quantity
      }
      history {
        eventId
        kind
        occurredAt
        actorUserId
      }
    }
  }
`);
