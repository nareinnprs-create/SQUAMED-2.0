'use client';

import { useState } from 'react';
import { Button, Input, Modal, Select, StatusIndicator, Textarea } from '@squamed/ui';
import { useApi } from '@/lib/use-api';

const CHANNEL_TYPES = ['direct', 'group', 'department', 'hospital'];

interface NewChannelModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}

export function NewChannelModal({ open, onClose, onCreated }: NewChannelModalProps) {
  const { apiFetch, isReady } = useApi();
  const [name, setName] = useState('');
  const [type, setType] = useState('group');
  const [description, setDescription] = useState('');
  const [memberIds, setMemberIds] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  async function handleCreate() {
    if (!isReady || creating) return;
    setCreating(true);
    setError(null);
    try {
      const parsedMemberIds = memberIds
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean);
      await apiFetch('/communication/channels', {
        method: 'POST',
        body: JSON.stringify({
          name: name.trim(),
          type,
          description: description.trim() ? description.trim() : null,
          memberIds: parsedMemberIds,
        }),
      });
      setName('');
      setType('group');
      setDescription('');
      setMemberIds('');
      onCreated();
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create channel');
    } finally {
      setCreating(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="New channel"
      description="Create a direct, group or department channel to start messaging."
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={creating} disabled={!name.trim()} onClick={() => void handleCreate()}>
            Create channel
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {error ? <StatusIndicator tone="critical" label={error} /> : null}
        <Select label="Type" value={type} onChange={(e) => setType(e.target.value)}>
          {CHANNEL_TYPES.map((channelType) => (
            <option key={channelType} value={channelType}>
              {channelType}
            </option>
          ))}
        </Select>
        <Input
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Cardiology Team"
          required
        />
        <Textarea
          label="Description"
          rows={2}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="What is this channel for?"
        />
        <Input
          label="Member IDs"
          value={memberIds}
          onChange={(e) => setMemberIds(e.target.value)}
          placeholder="Comma-separated user IDs, e.g. 3f2c…,9a1b…"
          hint="All users you add will receive a channel membership."
        />
      </div>
    </Modal>
  );
}