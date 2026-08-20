/**
 * 航海スケジュールの永続化（MyBatis）。
 *
 * <p>ドメインは永続化を知らない。ここが行（Record）と集約の変換を引き受ける。
 * 復元では検査しない（検査を後から足すと、その規則が無かったころの行が読めなくなる）。
 */
package com.example.routingms.infrastructure.persistence;
