/* eslint-disable */
import * as types from './graphql';
import type { TypedDocumentNode as DocumentNode } from '@graphql-typed-document-node/core';

/**
 * Map of all GraphQL operations in the project.
 *
 * This map has several performance disadvantages:
 * 1. It is not tree-shakeable, so it will include all operations in the project.
 * 2. It is not minifiable, so the string of a GraphQL query will be multiple times inside the bundle.
 * 3. It does not support dead code elimination, so it will add unused operations.
 *
 * Therefore it is highly recommended to use the babel or swc plugin for production.
 * Learn more about it here: https://the-guild.dev/graphql/codegen/plugins/presets/preset-client#reducing-bundle-size
 */
type Documents = {
    "\n  query Shippers($keyword: String) {\n    shippers(keyword: $keyword) {\n      id\n      name\n      email\n      kind\n    }\n  }\n": typeof types.ShippersDocument,
    "\n  query Shipper($id: String!) {\n    shipper(id: $id) {\n      id\n      kind\n      name\n      address\n      contact\n      email\n    }\n  }\n": typeof types.ShipperDocument,
    "\n  query Bookings($status: String, $shipperId: String) {\n    bookings(status: $status, shipperId: $shipperId) {\n      id\n      number\n      shipperId\n      status\n      origin\n      destination\n      deadline\n      version\n      availableCommands\n    }\n  }\n": typeof types.BookingsDocument,
    "\n  query Booking($id: String!) {\n    booking(id: $id) {\n      id\n      number\n      shipperId\n      status\n      version\n      availableCommands\n      origin\n      destination\n      deadline\n      preferredHandover\n      cargo {\n        itemName\n        weightKg\n        quantity\n      }\n      history {\n        eventId\n        kind\n        occurredAt\n        actorUserId\n      }\n    }\n  }\n": typeof types.BookingDocument,
};
const documents: Documents = {
    "\n  query Shippers($keyword: String) {\n    shippers(keyword: $keyword) {\n      id\n      name\n      email\n      kind\n    }\n  }\n": types.ShippersDocument,
    "\n  query Shipper($id: String!) {\n    shipper(id: $id) {\n      id\n      kind\n      name\n      address\n      contact\n      email\n    }\n  }\n": types.ShipperDocument,
    "\n  query Bookings($status: String, $shipperId: String) {\n    bookings(status: $status, shipperId: $shipperId) {\n      id\n      number\n      shipperId\n      status\n      origin\n      destination\n      deadline\n      version\n      availableCommands\n    }\n  }\n": types.BookingsDocument,
    "\n  query Booking($id: String!) {\n    booking(id: $id) {\n      id\n      number\n      shipperId\n      status\n      version\n      availableCommands\n      origin\n      destination\n      deadline\n      preferredHandover\n      cargo {\n        itemName\n        weightKg\n        quantity\n      }\n      history {\n        eventId\n        kind\n        occurredAt\n        actorUserId\n      }\n    }\n  }\n": types.BookingDocument,
};

/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 *
 *
 * @example
 * ```ts
 * const query = graphql(`query GetUser($id: ID!) { user(id: $id) { name } }`);
 * ```
 *
 * The query argument is unknown!
 * Please regenerate the types.
 */
export function graphql(source: string): unknown;

/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query Shippers($keyword: String) {\n    shippers(keyword: $keyword) {\n      id\n      name\n      email\n      kind\n    }\n  }\n"): (typeof documents)["\n  query Shippers($keyword: String) {\n    shippers(keyword: $keyword) {\n      id\n      name\n      email\n      kind\n    }\n  }\n"];
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query Shipper($id: String!) {\n    shipper(id: $id) {\n      id\n      kind\n      name\n      address\n      contact\n      email\n    }\n  }\n"): (typeof documents)["\n  query Shipper($id: String!) {\n    shipper(id: $id) {\n      id\n      kind\n      name\n      address\n      contact\n      email\n    }\n  }\n"];
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query Bookings($status: String, $shipperId: String) {\n    bookings(status: $status, shipperId: $shipperId) {\n      id\n      number\n      shipperId\n      status\n      origin\n      destination\n      deadline\n      version\n      availableCommands\n    }\n  }\n"): (typeof documents)["\n  query Bookings($status: String, $shipperId: String) {\n    bookings(status: $status, shipperId: $shipperId) {\n      id\n      number\n      shipperId\n      status\n      origin\n      destination\n      deadline\n      version\n      availableCommands\n    }\n  }\n"];
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  query Booking($id: String!) {\n    booking(id: $id) {\n      id\n      number\n      shipperId\n      status\n      version\n      availableCommands\n      origin\n      destination\n      deadline\n      preferredHandover\n      cargo {\n        itemName\n        weightKg\n        quantity\n      }\n      history {\n        eventId\n        kind\n        occurredAt\n        actorUserId\n      }\n    }\n  }\n"): (typeof documents)["\n  query Booking($id: String!) {\n    booking(id: $id) {\n      id\n      number\n      shipperId\n      status\n      version\n      availableCommands\n      origin\n      destination\n      deadline\n      preferredHandover\n      cargo {\n        itemName\n        weightKg\n        quantity\n      }\n      history {\n        eventId\n        kind\n        occurredAt\n        actorUserId\n      }\n    }\n  }\n"];

export function graphql(source: string) {
  return (documents as any)[source] ?? {};
}

export type DocumentType<TDocumentNode extends DocumentNode<any, any>> = TDocumentNode extends DocumentNode<  infer TType,  any>  ? TType  : never;