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
    "\n  mutation RegisterShipper(\n    $idempotencyKey: String!\n    $name: String!\n    $address: String!\n    $contact: String!\n    $email: String!\n    $acknowledgedDuplicate: Boolean\n  ) {\n    registerShipper(\n      idempotencyKey: $idempotencyKey\n      name: $name\n      address: $address\n      contact: $contact\n      email: $email\n      acknowledgedDuplicate: $acknowledgedDuplicate\n    ) {\n      shipperId\n    }\n  }\n": typeof types.RegisterShipperDocument,
    "\n  mutation RegisterBooking(\n    $idempotencyKey: String!\n    $bookingId: String!\n    $shipperId: String!\n    $itemName: String!\n    $weightKg: String!\n    $lengthCm: String!\n    $widthCm: String!\n    $heightCm: String!\n    $quantity: Int!\n    $origin: String!\n    $destination: String!\n    $preferredHandover: String!\n    $deadline: String!\n  ) {\n    registerBooking(\n      idempotencyKey: $idempotencyKey\n      bookingId: $bookingId\n      shipperId: $shipperId\n      itemName: $itemName\n      weightKg: $weightKg\n      lengthCm: $lengthCm\n      widthCm: $widthCm\n      heightCm: $heightCm\n      quantity: $quantity\n      origin: $origin\n      destination: $destination\n      preferredHandover: $preferredHandover\n      deadline: $deadline\n    ) {\n      bookingId\n      number\n      version\n    }\n  }\n": typeof types.RegisterBookingDocument,
};
const documents: Documents = {
    "\n  mutation RegisterShipper(\n    $idempotencyKey: String!\n    $name: String!\n    $address: String!\n    $contact: String!\n    $email: String!\n    $acknowledgedDuplicate: Boolean\n  ) {\n    registerShipper(\n      idempotencyKey: $idempotencyKey\n      name: $name\n      address: $address\n      contact: $contact\n      email: $email\n      acknowledgedDuplicate: $acknowledgedDuplicate\n    ) {\n      shipperId\n    }\n  }\n": types.RegisterShipperDocument,
    "\n  mutation RegisterBooking(\n    $idempotencyKey: String!\n    $bookingId: String!\n    $shipperId: String!\n    $itemName: String!\n    $weightKg: String!\n    $lengthCm: String!\n    $widthCm: String!\n    $heightCm: String!\n    $quantity: Int!\n    $origin: String!\n    $destination: String!\n    $preferredHandover: String!\n    $deadline: String!\n  ) {\n    registerBooking(\n      idempotencyKey: $idempotencyKey\n      bookingId: $bookingId\n      shipperId: $shipperId\n      itemName: $itemName\n      weightKg: $weightKg\n      lengthCm: $lengthCm\n      widthCm: $widthCm\n      heightCm: $heightCm\n      quantity: $quantity\n      origin: $origin\n      destination: $destination\n      preferredHandover: $preferredHandover\n      deadline: $deadline\n    ) {\n      bookingId\n      number\n      version\n    }\n  }\n": types.RegisterBookingDocument,
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
export function graphql(source: "\n  mutation RegisterShipper(\n    $idempotencyKey: String!\n    $name: String!\n    $address: String!\n    $contact: String!\n    $email: String!\n    $acknowledgedDuplicate: Boolean\n  ) {\n    registerShipper(\n      idempotencyKey: $idempotencyKey\n      name: $name\n      address: $address\n      contact: $contact\n      email: $email\n      acknowledgedDuplicate: $acknowledgedDuplicate\n    ) {\n      shipperId\n    }\n  }\n"): (typeof documents)["\n  mutation RegisterShipper(\n    $idempotencyKey: String!\n    $name: String!\n    $address: String!\n    $contact: String!\n    $email: String!\n    $acknowledgedDuplicate: Boolean\n  ) {\n    registerShipper(\n      idempotencyKey: $idempotencyKey\n      name: $name\n      address: $address\n      contact: $contact\n      email: $email\n      acknowledgedDuplicate: $acknowledgedDuplicate\n    ) {\n      shipperId\n    }\n  }\n"];
/**
 * The graphql function is used to parse GraphQL queries into a document that can be used by GraphQL clients.
 */
export function graphql(source: "\n  mutation RegisterBooking(\n    $idempotencyKey: String!\n    $bookingId: String!\n    $shipperId: String!\n    $itemName: String!\n    $weightKg: String!\n    $lengthCm: String!\n    $widthCm: String!\n    $heightCm: String!\n    $quantity: Int!\n    $origin: String!\n    $destination: String!\n    $preferredHandover: String!\n    $deadline: String!\n  ) {\n    registerBooking(\n      idempotencyKey: $idempotencyKey\n      bookingId: $bookingId\n      shipperId: $shipperId\n      itemName: $itemName\n      weightKg: $weightKg\n      lengthCm: $lengthCm\n      widthCm: $widthCm\n      heightCm: $heightCm\n      quantity: $quantity\n      origin: $origin\n      destination: $destination\n      preferredHandover: $preferredHandover\n      deadline: $deadline\n    ) {\n      bookingId\n      number\n      version\n    }\n  }\n"): (typeof documents)["\n  mutation RegisterBooking(\n    $idempotencyKey: String!\n    $bookingId: String!\n    $shipperId: String!\n    $itemName: String!\n    $weightKg: String!\n    $lengthCm: String!\n    $widthCm: String!\n    $heightCm: String!\n    $quantity: Int!\n    $origin: String!\n    $destination: String!\n    $preferredHandover: String!\n    $deadline: String!\n  ) {\n    registerBooking(\n      idempotencyKey: $idempotencyKey\n      bookingId: $bookingId\n      shipperId: $shipperId\n      itemName: $itemName\n      weightKg: $weightKg\n      lengthCm: $lengthCm\n      widthCm: $widthCm\n      heightCm: $heightCm\n      quantity: $quantity\n      origin: $origin\n      destination: $destination\n      preferredHandover: $preferredHandover\n      deadline: $deadline\n    ) {\n      bookingId\n      number\n      version\n    }\n  }\n"];

export function graphql(source: string) {
  return (documents as any)[source] ?? {};
}

export type DocumentType<TDocumentNode extends DocumentNode<any, any>> = TDocumentNode extends DocumentNode<  infer TType,  any>  ? TType  : never;