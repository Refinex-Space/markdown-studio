/** Language-server documents (`jdt://…`): the IDE opens such links itself, so none are virtual here. */
export function isVirtualUri(_uri: string): boolean {
  return false;
}
