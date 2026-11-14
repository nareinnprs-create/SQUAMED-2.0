import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import type { ReactElement } from 'react';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

export interface Mounted {
  container: HTMLElement;
  root: Root;
  unmount: () => void;
}

export function mount(element: ReactElement): Mounted {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(element);
  });
  return {
    container,
    root,
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}

/** Lets pending effects and promises settle inside act(). */
export async function flush(): Promise<void> {
  await act(async () => {});
}

function nativeValueSetter(target: HTMLInputElement | HTMLSelectElement, value: string): void {
  const setter = Object.getOwnPropertyDescriptor(
    target instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLSelectElement.prototype,
    'value',
  )?.set;
  if (!setter) throw new Error('Native value setter unavailable');
  setter.call(target, value);
}

export function inputByLabel(container: HTMLElement, label: string): HTMLInputElement {
  const labelEl = Array.from(container.querySelectorAll('label')).find(
    (entry) => entry.textContent?.replace(/\s*\*\s*$/, '').trim() === label,
  );
  const input = labelEl?.htmlFor ? document.getElementById(labelEl.htmlFor) : null;
  if (!(input instanceof HTMLInputElement)) throw new Error(`No input labelled "${label}"`);
  return input;
}

export function selectByLabel(container: HTMLElement, label: string): HTMLSelectElement {
  const labelEl = Array.from(container.querySelectorAll('label')).find(
    (entry) => entry.textContent?.replace(/\s*\*\s*$/, '').trim() === label,
  );
  const select = labelEl?.htmlFor ? document.getElementById(labelEl.htmlFor) : null;
  if (!(select instanceof HTMLSelectElement)) throw new Error(`No select labelled "${label}"`);
  return select;
}

export async function typeInto(container: HTMLElement, label: string, value: string): Promise<void> {
  const input = inputByLabel(container, label);
  await act(async () => {
    nativeValueSetter(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

export async function chooseOption(container: HTMLElement, label: string, value: string): Promise<void> {
  const select = selectByLabel(container, label);
  await act(async () => {
    nativeValueSetter(select, value);
    select.dispatchEvent(new Event('change', { bubbles: true }));
  });
}

export async function clickButton(container: HTMLElement, text: string): Promise<void> {
  const button = Array.from(container.querySelectorAll('button')).find((entry) => entry.textContent?.trim() === text);
  if (!button) throw new Error(`No button labelled "${text}"`);
  await act(async () => {
    button.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
  });
}

export function textIncludes(container: HTMLElement, text: string): boolean {
  return container.textContent?.includes(text) ?? false;
}

/** Deterministic api-fetch test double: records calls, replays queued responses. */
export interface RecordedCall {
  path: string;
  options?: RequestInit;
}

export function fixtureApi(
  respond: (path: string, options?: RequestInit) => unknown | Promise<unknown>,
): { apiFetch: <T>(path: string, options?: RequestInit) => Promise<T>; calls: RecordedCall[] } {
  const calls: RecordedCall[] = [];
  return {
    calls,
    apiFetch: async <T,>(path: string, options?: RequestInit) => {
      calls.push({ path, options });
      const result = await respond(path, options);
      return result as T;
    },
  };
}
