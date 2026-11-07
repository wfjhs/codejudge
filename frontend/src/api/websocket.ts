// 判题结果 WebSocket 客户端工具。
// 用法：提交后调用 connectJudgeWs，服务端判题完成会推送结果；
// 若 WS 不可用（如后端老版本没开 WS），onError 触发，调用方回退到 HTTP 轮询。

export interface JudgePushPayload {
  submissionId: number;
  status: string;
  score: number;
  timeCost: number | null;
  memoryCost: number | null;
  errorMsg: string | null;
  failCase: number | null;
}

export interface JudgeWsHandlers {
  onResult?: (payload: JudgePushPayload) => void;
  onError?: () => void;
}

export interface JudgeWsConnection {
  close: () => void;
}

export function connectJudgeWs(
  submissionId: number,
  token: string,
  handlers: JudgeWsHandlers,
): JudgeWsConnection {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  const url = `${proto}://${location.host}/ws/judge/${submissionId}?token=${encodeURIComponent(token)}`;

  let closedByUs = false;
  let ws: WebSocket | null = null;

  const close = () => {
    closedByUs = true;
    ws?.close();
  };

  try {
    ws = new WebSocket(url);
  } catch {
    handlers.onError?.();
    return { close };
  }

  ws.onmessage = (ev) => {
    try {
      const data = JSON.parse(ev.data) as JudgePushPayload;
      handlers.onResult?.(data);
    } catch {
      // 解析失败忽略
    }
  };

  ws.onerror = () => {
    if (!closedByUs) handlers.onError?.();
  };

  ws.onclose = () => {
    if (!closedByUs) handlers.onError?.();
  };

  return { close };
}
