'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Badge,
  Button,
  ContentPanel,
  PageHeader,
  Spinner,
  StatusIndicator,
  Textarea,
} from '@squamed/ui';
import { useApi } from '@/lib/use-api';
import { useRealtime } from '@/providers/RealtimeProvider';
import { NewChannelModal } from '@/components/communication/new-channel-modal';

interface ChannelSummary {
  id: string | null;
  name: string;
  type: string;
  description: string | null;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
  memberCount: number;
}

interface ChannelView {
  id: string | null;
  hospitalId: string | null;
  name: string;
  type: string;
  description: string | null;
  ownerId: string | null;
  patientId: string | null;
  encounterId: string | null;
  isArchived: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

interface ChannelMemberView {
  id: string | null;
  channelId: string;
  userId: string;
  role: string;
  lastReadAt: string | null;
  mutedUntil: string | null;
  notificationsEnabled: boolean;
}

interface ChannelDetail {
  channel: ChannelView;
  members: ChannelMemberView[];
  unreadCount: number;
}

interface MessageView {
  id: string | null;
  channelId: string;
  senderId: string;
  senderName: string | null;
  body: string;
  messageType: string;
  attachmentCount: number;
  createdAt: string | null;
  editedAt: string | null;
  deletedAt: string | null;
}

const CHANNEL_TYPE_LABELS: Record<string, string> = {
  direct: 'Direct',
  group: 'Group',
  department: 'Department',
  hospital: 'Hospital',
};

function formatTime(value: string | null): string {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export default function CommunicationPage() {
  const { apiFetch, isReady } = useApi();
  const { lastEvent } = useRealtime();
  const [channels, setChannels] = useState<ChannelSummary[]>([]);
  const [unreadTotal, setUnreadTotal] = useState(0);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ChannelDetail | null>(null);
  const [messages, setMessages] = useState<MessageView[]>([]);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [loadingChannel, setLoadingChannel] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const threadRef = useRef<HTMLDivElement>(null);

  const loadChannels = useCallback(async () => {
    if (!isReady) return;
    try {
      const [items, count] = await Promise.all([
        apiFetch<ChannelSummary[]>('/communication/channels'),
        apiFetch<number>('/communication/unread-count'),
      ]);
      setChannels(Array.isArray(items) ? items : []);
      setUnreadTotal(typeof count === 'number' ? count : 0);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load channels');
    }
  }, [apiFetch, isReady]);

  const loadThread = useCallback(
    async (channelId: string) => {
      if (!isReady) return;
      setLoadingChannel(channelId);
      try {
        const [channelDetail, channelMessages] = await Promise.all([
          apiFetch<ChannelDetail>(`/communication/channels/${channelId}`),
          apiFetch<MessageView[]>(`/communication/channels/${channelId}/messages?limit=50`),
        ]);
        setDetail(channelDetail);
        setMessages(Array.isArray(channelMessages) ? channelMessages : []);
        setError(null);
        await apiFetch(`/communication/channels/${channelId}/read`, { method: 'POST', body: '{}' });
        await loadChannels();
      } catch (e) {
        setError(e instanceof Error ? e.message : 'Failed to load channel');
      } finally {
        setLoadingChannel(null);
      }
    },
    [apiFetch, isReady, loadChannels],
  );

  useEffect(() => {
    if (!isReady) return;
    void loadChannels();
  }, [isReady, loadChannels]);

  useEffect(() => {
    if (!lastEvent || typeof lastEvent !== 'object') return;
    const event = lastEvent as { type?: string; message?: MessageView; notification?: unknown };
    if (event.type === 'message.sent' && event.message) {
      const message = event.message;
      if (selectedId && message.channelId === selectedId) {
        setMessages((previous) => {
          if (previous.some((item) => item.id && item.id === message.id)) return previous;
          return [...previous, message];
        });
      }
    }
    if (event.type === 'notification.created' || event.type === 'event.received') {
      void loadChannels();
    }
  }, [lastEvent, selectedId, loadChannels]);

  useEffect(() => {
    if (threadRef.current) {
      threadRef.current.scrollTop = threadRef.current.scrollHeight;
    }
  }, [messages]);

  function selectChannel(channelId: string) {
    setSelectedId(channelId);
    void loadThread(channelId);
  }

  async function sendMessage() {
    const body = draft.trim();
    if (!selectedId || !body || sending) return;
    setSending(true);
    try {
      const sent = await apiFetch<MessageView>(`/communication/channels/${selectedId}/messages`, {
        method: 'POST',
        body: JSON.stringify({ body, messageType: 'text' }),
      });
      setMessages((previous) => {
        if (previous.some((item) => item.id && item.id === sent.id)) return previous;
        return [...previous, sent];
      });
      setDraft('');
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to send message');
    } finally {
      setSending(false);
    }
  }

  const selectedChannel = channels.find((channel) => channel.id === selectedId) ?? null;
  const channelLoading = selectedId !== null && loadingChannel === selectedId;

  return (
    <div style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 16 }}>
      <PageHeader
        title="Communication"
        description="Team channels and secure messaging — delivered in real time."
        actions={
          <>
            <Badge variant="information" appearance="filled">
              {unreadTotal} unread
            </Badge>
            <Button onClick={() => setModalOpen(true)}>New channel</Button>
          </>
        }
      />

      {error ? <StatusIndicator tone="critical" label={error} /> : null}

      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'minmax(260px, 340px) minmax(0, 1fr)',
          gap: 16,
          alignItems: 'stretch',
        }}
      >
        <ContentPanel
          style={{
            display: 'flex',
            flexDirection: 'column',
            height: 'calc(100vh - 230px)',
            padding: 0,
            overflow: 'hidden',
          }}
        >
          <div
            style={{
              padding: '12px 16px',
              fontSize: 13,
              fontWeight: 600,
              color: 'var(--sq-fg-muted)',
              borderBottom: '1px solid var(--sq-border)',
            }}
          >
            Channels
          </div>
          <div style={{ flex: 1, overflowY: 'auto' }}>
            {channels.length === 0 ? (
              <div
                style={{
                  padding: '28px 16px',
                  textAlign: 'center',
                  fontSize: 13,
                  color: 'var(--sq-fg-muted)',
                }}
              >
                No channels yet.
              </div>
            ) : (
              channels.map((channel) => {
                const active = channel.id === selectedId;
                return (
                  <button
                    key={channel.id ?? channel.name}
                    type="button"
                    onClick={() => {
                      if (channel.id) selectChannel(channel.id);
                    }}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'stretch',
                      gap: 6,
                      width: '100%',
                      padding: '10px 16px',
                      border: 'none',
                      borderBottom: '1px solid var(--sq-border)',
                      boxSizing: 'border-box',
                      backgroundColor: active ? 'color-mix(in srgb, var(--sq-primary) 8%, transparent)' : 'transparent',
                      cursor: 'pointer',
                      textAlign: 'left',
                      fontFamily: 'inherit',
                    }}
                  >
                    <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <strong style={{ fontSize: 14, color: 'var(--sq-fg)', flex: 1, minWidth: 0 }}>
                        {channel.name}
                      </strong>
                      {channel.unreadCount > 0 ? (
                        <Badge variant="information" size="sm">
                          {channel.unreadCount}
                        </Badge>
                      ) : null}
                    </span>
                    <span
                      style={{
                        display: 'block',
                        fontSize: 12,
                        color: 'var(--sq-fg-muted)',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {channel.lastMessagePreview ?? 'No messages yet'}
                    </span>
                    <span
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 6,
                        fontSize: 11,
                        color: 'var(--sq-fg-muted)',
                      }}
                    >
                      <Badge variant="neutral" size="sm">
                        {CHANNEL_TYPE_LABELS[channel.type] ?? channel.type}
                      </Badge>
                      <span>{channel.memberCount} members</span>
                      {channel.lastMessageAt ? <span>· {formatTime(channel.lastMessageAt)}</span> : null}
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </ContentPanel>

        <ContentPanel
          style={{
            display: 'flex',
            flexDirection: 'column',
            height: 'calc(100vh - 230px)',
            padding: 0,
            overflow: 'hidden',
          }}
        >
          {!selectedId ? (
            <div
              style={{
                flex: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: 32,
                fontSize: 14,
                color: 'var(--sq-fg-muted)',
                textAlign: 'center',
              }}
            >
              Select a channel to start messaging.
            </div>
          ) : (
            <>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 12,
                  padding: '12px 16px',
                  borderBottom: '1px solid var(--sq-border)',
                }}
              >
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
                  <strong style={{ fontSize: 14, color: 'var(--sq-fg)' }}>
                    {selectedChannel?.name ?? detail?.channel.name ?? ''}
                  </strong>
                  <span style={{ fontSize: 12, color: 'var(--sq-fg-muted)' }}>
                    {detail ? `${detail.members.length} members` : 'Loading members…'}
                    {selectedChannel?.description ? ` — ${selectedChannel.description}` : ''}
                  </span>
                </div>
                {channelLoading ? <Spinner size="sm" /> : null}
              </div>

              <div ref={threadRef} style={{ flex: 1, overflowY: 'auto', padding: 16 }}>
                {channelLoading ? (
                  <div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
                    <Spinner />
                  </div>
                ) : messages.length === 0 ? (
                  <div
                    style={{
                      padding: '28px 16px',
                      textAlign: 'center',
                      fontSize: 13,
                      color: 'var(--sq-fg-muted)',
                    }}
                  >
                    No messages yet. Say hello!
                  </div>
                ) : (
                  messages
                    .filter((message) => message.deletedAt === null)
                    .map((message) => (
                      <div
                        key={message.id ?? `${message.channelId}-${message.createdAt}`}
                        style={{
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'flex-start',
                          gap: 4,
                          marginBottom: 14,
                        }}
                      >
                        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <strong style={{ fontSize: 13, color: 'var(--sq-fg)' }}>
                            {message.senderName ?? 'Unknown user'}
                          </strong>
                          <span style={{ fontSize: 11, color: 'var(--sq-fg-muted)' }}>
                            {formatTime(message.createdAt)}
                          </span>
                        </span>
                        <div
                          style={{
                            maxWidth: '75%',
                            padding: '8px 12px',
                            borderRadius: 12,
                            borderTopLeftRadius: 4,
                            backgroundColor: 'color-mix(in srgb, var(--sq-fg) 7%, transparent)',
                            fontSize: 14,
                            color: 'var(--sq-fg)',
                            overflowWrap: 'break-word',
                            whiteSpace: 'pre-wrap',
                          }}
                        >
                          {message.body}
                        </div>
                      </div>
                    ))
                )}
              </div>

              <div
                style={{
                  display: 'flex',
                  alignItems: 'flex-end',
                  gap: 10,
                  padding: '12px 16px',
                  borderTop: '1px solid var(--sq-border)',
                }}
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <Textarea
                    rows={2}
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    placeholder="Type a message… (Enter to send)"
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        void sendMessage();
                      }
                    }}
                  />
                </div>
                <Button loading={sending} disabled={!draft.trim()} onClick={() => void sendMessage()}>
                  Send
                </Button>
              </div>
            </>
          )}
        </ContentPanel>
      </div>

      <NewChannelModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        onCreated={() => {
          void loadChannels();
        }}
      />
    </div>
  );
}