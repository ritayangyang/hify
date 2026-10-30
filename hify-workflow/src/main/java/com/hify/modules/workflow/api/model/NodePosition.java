package com.hify.modules.workflow.api.model;

/**
 * 节点在前端画布上的坐标。后端不解释，仅随节点存取原样返回。
 *
 * @param x 横坐标
 * @param y 纵坐标
 */
public record NodePosition(Double x, Double y) {
}
